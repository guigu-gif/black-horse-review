package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.RecommendItemVO;
import com.hmdp.dto.RecommendRequest;
import com.hmdp.dto.RecommendResponse;
import com.hmdp.dto.RecommendSession;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IRecommendService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RecommendServiceImpl implements IRecommendService {

    private static final int CANDIDATE_SIZE = 20;
    private static final int RESULT_SIZE = 4;
    // remaining 低于这个值时触发预加载（约2批）
    private static final int PREFETCH_THRESHOLD = RESULT_SIZE * 2;
    private static final String FINISHED_MESSAGE = "这一轮推荐已经刷完了";

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.chat-url}")
    private String chatUrl;

    @Value("${deepseek.chat-model}")
    private String chatModel;

    @Resource
    private IShopService shopService;

    @Resource
    private IShopTypeService shopTypeService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result recommend(RecommendRequest request) {
        String supplement = normalizeSupplement(request.getSupplement());
        int hour = request.getHour() == null ? LocalTime.now().getHour() : request.getHour();
        List<String> excludeTags = normalizeTags(request.getExcludeTags());
        List<Long> excludedShopIds = normalizeIds(request.getExcludedShopIds());
        boolean restart = Boolean.TRUE.equals(request.getRestart());

        RecommendSession session = loadSession(request.getSessionId());
        if (shouldCreateNewSession(session, supplement, hour, excludeTags, restart)) {
            session = buildSession(request.getSessionId(), supplement, hour, excludeTags, excludedShopIds);
        } else {
            session.setExcludedShopIds(excludedShopIds);
        }

        List<Long> remainingIds = getRemainingIds(session);
        if (remainingIds.size() <= PREFETCH_THRESHOLD) {
            prefetchCandidates(session);
            remainingIds = getRemainingIds(session); // 重新计算，候选池可能已扩充
        }
        if (remainingIds.isEmpty()) {
            saveSession(session);
            return Result.ok(buildResponse(session.getSessionId(), Collections.emptyList(), true, FINISHED_MESSAGE));
        }

        List<Long> batchIds = remainingIds.subList(0, Math.min(RESULT_SIZE, remainingIds.size()));
        List<Shop> batchShops = listShopsByIds(batchIds);
        if (batchShops.isEmpty()) {
            saveSession(session);
            return Result.ok(buildResponse(session.getSessionId(), Collections.emptyList(), true, FINISHED_MESSAGE));
        }

        Map<Long, String> typeMap = shopTypeService.list().stream()
                .collect(Collectors.toMap(ShopType::getId, ShopType::getName, (a, b) -> a));

        List<Map.Entry<Shop, List<String>>> taggedShops = batchShops.stream()
                .map(shop -> new java.util.AbstractMap.SimpleEntry<>(shop, buildTags(shop, typeMap)))
                .collect(Collectors.toList());

        Map<Long, String> reasonMap = callAiForReasons(taggedShops, supplement, hour);
        List<RecommendItemVO> items = taggedShops.stream().map(entry -> toRecommendItem(entry, reasonMap)).collect(Collectors.toList());

        List<Long> shownShopIds = session.getShownShopIds();
        shownShopIds.addAll(batchIds);
        session.setShownShopIds(normalizeIds(shownShopIds));
        saveSession(session);

        boolean finished = getRemainingIds(session).isEmpty();
        String message = finished ? FINISHED_MESSAGE : null;
        return Result.ok(buildResponse(session.getSessionId(), items, finished, message));
    }

    private RecommendResponse buildResponse(String sessionId, List<RecommendItemVO> items, boolean finished, String message) {
        RecommendResponse response = new RecommendResponse();
        response.setSessionId(sessionId);
        response.setItems(items);
        response.setFinished(finished);
        response.setMessage(message);
        return response;
    }

    private RecommendSession buildSession(
            String requestedSessionId,
            String supplement,
            int hour,
            List<String> excludeTags,
            List<Long> excludedShopIds
    ) {
        RecommendSession session = new RecommendSession();
        session.setSessionId(StrUtil.isNotBlank(requestedSessionId) ? requestedSessionId : UUID.randomUUID().toString());
        UserDTO user = UserHolder.getUser();
        session.setUserId(user == null ? 0L : user.getId());
        session.setSupplement(supplement);
        session.setHour(hour);
        session.setExcludeTags(excludeTags);
        session.setExcludedShopIds(excludedShopIds);
        session.setShownShopIds(new ArrayList<>());

        Map<Long, String> typeMap = shopTypeService.list().stream()
                .collect(Collectors.toMap(ShopType::getId, ShopType::getName, (a, b) -> a));

        List<Shop> candidates = shopService.lambdaQuery()
                .orderByDesc(Shop::getScore)
                .last("LIMIT " + CANDIDATE_SIZE)
                .list();

        List<Long> candidateIds = candidates.stream()
                .filter(shop -> buildTags(shop, typeMap).stream().noneMatch(excludeTags::contains))
                .map(Shop::getId)
                .collect(Collectors.toList());

        session.setCandidateShopIds(candidateIds);
        session.setCandidateOffset(CANDIDATE_SIZE); // 记录已取数量，下次预加载从这里开始
        saveSession(session);
        return session;
    }

    private void prefetchCandidates(RecommendSession session) {
        int offset = session.getCandidateOffset();
        Map<Long, String> typeMap = shopTypeService.list().stream()
                .collect(Collectors.toMap(ShopType::getId, ShopType::getName, (a, b) -> a));

        List<Shop> next = shopService.lambdaQuery()
                .orderByDesc(Shop::getScore)
                .last("LIMIT " + CANDIDATE_SIZE + " OFFSET " + offset)
                .list();

        if (next.isEmpty()) {
            return; // 数据库已无更多店铺，不更新 offset，保持原状
        }

        List<String> excludeTags = normalizeTags(session.getExcludeTags());
        Set<Long> existing = new LinkedHashSet<>(normalizeIds(session.getCandidateShopIds()));

        List<Long> newIds = next.stream()
                .filter(shop -> buildTags(shop, typeMap).stream().noneMatch(excludeTags::contains))
                .map(Shop::getId)
                .filter(id -> !existing.contains(id)) // 防止极端情况下重复
                .collect(Collectors.toList());

        List<Long> merged = new ArrayList<>(session.getCandidateShopIds());
        merged.addAll(newIds);
        session.setCandidateShopIds(merged);
        session.setCandidateOffset(offset + next.size());
    }

    private boolean shouldCreateNewSession(
            RecommendSession session,
            String supplement,
            int hour,
            List<String> excludeTags,
            boolean restart
    ) {
        if (session == null || restart) {
            return true;
        }
        if (!Objects.equals(session.getSupplement(), supplement)) {
            return true;
        }
        if (!Objects.equals(session.getHour(), hour)) {
            return true;
        }
        return !Objects.equals(normalizeTags(session.getExcludeTags()), excludeTags);
    }

    private RecommendSession loadSession(String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(RedisConstants.RECOMMEND_SESSION_KEY + sessionId);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        RecommendSession session = JSONUtil.toBean(json, RecommendSession.class);
        session.setExcludeTags(normalizeTags(session.getExcludeTags()));
        session.setExcludedShopIds(normalizeIds(session.getExcludedShopIds()));
        session.setCandidateShopIds(normalizeIds(session.getCandidateShopIds()));
        session.setShownShopIds(normalizeIds(session.getShownShopIds()));
        return session;
    }

    private void saveSession(RecommendSession session) {
        stringRedisTemplate.opsForValue().set(
                RedisConstants.RECOMMEND_SESSION_KEY + session.getSessionId(),
                JSONUtil.toJsonStr(session),
                RedisConstants.RECOMMEND_SESSION_TTL,
                TimeUnit.SECONDS
        );
    }

    private List<Long> getRemainingIds(RecommendSession session) {
        Set<Long> shownSet = new LinkedHashSet<>(normalizeIds(session.getShownShopIds()));
        Set<Long> excludedSet = new LinkedHashSet<>(normalizeIds(session.getExcludedShopIds()));
        return normalizeIds(session.getCandidateShopIds()).stream()
                .filter(id -> !shownSet.contains(id))
                .filter(id -> !excludedSet.contains(id))
                .collect(Collectors.toList());
    }

    private List<Shop> listShopsByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        String idStr = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        return shopService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list();
    }

    private RecommendItemVO toRecommendItem(Map.Entry<Shop, List<String>> entry, Map<Long, String> reasonMap) {
        Shop shop = entry.getKey();
        RecommendItemVO vo = new RecommendItemVO();
        vo.setId(shop.getId());
        vo.setName(shop.getName());
        vo.setScore(String.format("%.1f", shop.getScore() / 10.0));
        vo.setAvgPrice(shop.getAvgPrice());
        vo.setAddress(shop.getAddress() == null ? "" : shop.getAddress());
        vo.setOpenStatus(resolveOpenStatus(shop.getOpenHours()));
        vo.setTags(entry.getValue());
        vo.setReason(reasonMap.getOrDefault(shop.getId(), "综合评分高，这家值得一试。"));
        return vo;
    }

    private String normalizeSupplement(String supplement) {
        return supplement == null ? "" : supplement.trim();
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new ArrayList<>();
        }
        return tags.stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private List<String> buildTags(Shop shop, Map<Long, String> typeMap) {
        List<String> tags = new ArrayList<>();
        String typeName = typeMap.getOrDefault(shop.getTypeId(), "");
        if (StrUtil.isNotBlank(typeName)) {
            tags.add(typeName);
        }

        if (shop.getAvgPrice() != null) {
            long price = shop.getAvgPrice();
            if (price <= 30) {
                tags.add("￥10-30");
            } else if (price <= 50) {
                tags.add("￥30-50");
            } else if (price <= 80) {
                tags.add("￥50-80");
            } else {
                tags.add("￥80+");
            }
        }

        if (shop.getScore() != null && shop.getScore() >= 45) {
            tags.add("高分");
        }

        tags.add(resolveOpenStatus(shop.getOpenHours()));
        return tags;
    }

    private String resolveOpenStatus(String openHours) {
        if (StrUtil.isBlank(openHours)) {
            return "营业中";
        }
        if (openHours.contains("24")) {
            return "24h营业";
        }
        try {
            String[] parts = openHours.split("-");
            if (parts.length == 2) {
                LocalTime now = LocalTime.now();
                LocalTime open = LocalTime.parse(parts[0].trim());
                LocalTime close = LocalTime.parse(parts[1].trim());
                return now.isAfter(open) && now.isBefore(close) ? "营业中" : "已打烊";
            }
        } catch (Exception ignored) {
        }
        return "营业中";
    }

    private Map<Long, String> callAiForReasons(List<Map.Entry<Shop, List<String>>> shops, String supplement, int hour) {
        if (shops.isEmpty()) {
            return Collections.emptyMap();
        }

        String meal = hour < 10 ? "早餐" : hour < 14 ? "午餐" : hour < 17 ? "下午茶" : "晚餐";
        StringBuilder shopDesc = new StringBuilder();
        for (int i = 0; i < shops.size(); i++) {
            Shop shop = shops.get(i).getKey();
            List<String> tags = shops.get(i).getValue();
            shopDesc.append(i + 1).append(". ID=").append(shop.getId())
                    .append("，名称=").append(shop.getName())
                    .append("，标签=").append(String.join("/", tags))
                    .append("，均价=").append(shop.getAvgPrice()).append("元")
                    .append("，评分=").append(String.format("%.1f", shop.getScore() / 10.0))
                    .append("\n");
        }

        String userContext = supplement.isEmpty() ? "无额外要求" : "用户补充：" + supplement;
        String prompt = "你是一个美食推荐助手。当前餐段是" + meal + "，" + userContext + "。\n"
                + "请为下面每家店生成一句15到40字的推荐理由，口语化一点，必须只返回 JSON 数组："
                + "[{\"id\":1,\"reason\":\"...\"}]，不要输出别的内容。\n"
                + shopDesc;

        try {
            JSONObject body = new JSONObject();
            body.set("model", chatModel);
            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.set("role", "user");
            userMsg.set("content", prompt);
            messages.add(userMsg);
            body.set("messages", messages);
            body.set("temperature", 0.7);

            String response = HttpRequest.post(chatUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .timeout(20000)
                    .execute()
                    .body();

            JSONObject json = JSONUtil.parseObj(response);
            String content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getStr("content");

            content = content.replaceAll("(?s)```json\\s*|```\\s*", "").trim();
            JSONArray arr = JSONUtil.parseArray(content);
            Map<Long, String> result = new HashMap<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject item = arr.getJSONObject(i);
                result.put(item.getLong("id"), item.getStr("reason"));
            }
            return result;
        } catch (Exception e) {
            log.error("生成推荐理由失败", e);
            Map<Long, String> fallback = new HashMap<>();
            shops.forEach(entry -> fallback.put(entry.getKey().getId(), "综合评分高，这家值得一试。"));
            return fallback;
        }
    }
}
