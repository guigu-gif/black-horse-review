package com.hmdp.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 操作 Qdrant 向量数据库：建集合、存向量、查向量
 *
 * Qdrant 是个"向量版Redis"：
 *   - 集合(collection) ≈ Redis的key空间
 *   - 点(point)        ≈ 一条数据，包含 id + 向量 + 附加信息(payload)
 */
@Slf4j
@Service
public class QdrantService {

    @Value("${qdrant.url}")
    private String qdrantUrl;

    @Value("${qdrant.collection}")
    private String collection;

    @Value("${deepseek.embedding-dim}")
    private int embeddingDim;

    /**
     * 删除集合（维度配置改变时用）
     */
    public void deleteCollection() {
        String url = qdrantUrl + "/collections/" + collection;
        HttpResponse resp = HttpRequest.delete(url).timeout(5000).execute();
        log.info("删除集合结果: {}", resp.body());
    }

    /**
     * 初始化：如果集合不存在就创建它
     * 向量大小必须和 Embedding 模型输出维度一致
     */
    public void initCollection() {
        String url = qdrantUrl + "/collections/" + collection;

        // 先检查集合是否已存在
        HttpResponse checkResp = HttpRequest.get(url).timeout(5000).execute();
        if (checkResp.getStatus() == 200) {
            log.info("Qdrant 集合已存在: {}", collection);
            return;
        }

        // 不存在则创建
        JSONObject vectors = new JSONObject();
        vectors.set("size", embeddingDim);
        vectors.set("distance", "Cosine"); // 余弦相似度，适合文本

        JSONObject body = new JSONObject();
        body.set("vectors", vectors);

        HttpResponse resp = HttpRequest.put(url)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(5000)
                .execute();

        log.info("创建 Qdrant 集合结果: {}", resp.body());
    }

    /**
     * 存入一个商铺的向量
     * @param shopId  商铺 ID（作为 Qdrant point 的 id）
     * @param vector  商铺描述文字的向量
     * @param name    商铺名称（存在 payload 里，搜索结果时用）
     */
    public void upsert(Long shopId, List<Float> vector, String name) {
        String url = qdrantUrl + "/collections/" + collection + "/points";

        // 构造 payload（附加信息，不参与向量计算）
        JSONObject payload = new JSONObject();
        payload.set("shopId", shopId);
        payload.set("name", name);

        // 构造 point
        JSONObject point = new JSONObject();
        point.set("id", shopId);
        point.set("vector", vector);
        point.set("payload", payload);

        // Qdrant 批量 upsert 接口
        JSONArray points = new JSONArray();
        points.add(point);

        JSONObject body = new JSONObject();
        body.set("points", points);

        HttpResponse resp = HttpRequest.put(url)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(10000)
                .execute();

        if (resp.getStatus() != 200) {
            log.error("upsert 失败 shopId={}: {}", shopId, resp.body());
        }
    }

    /**
     * 语义搜索：找出和查询向量最相似的 top N 个商铺
     * @param queryVector 查询文字转成的向量
     * @param topN        返回前 N 个结果
     * @return 商铺 ID 列表（按相似度从高到低排序）
     */
    public List<Long> search(List<Float> queryVector, int topN) {
        String url = qdrantUrl + "/collections/" + collection + "/points/search";

        JSONObject body = new JSONObject();
        body.set("vector", queryVector);
        body.set("limit", topN);
        body.set("with_payload", true); // 返回 payload（包含 shopId）

        HttpResponse resp = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(10000)
                .execute();

        // 解析结果，提取 shopId
        log.info("Qdrant 搜索响应: {}", resp.body());
        List<Long> shopIds = new ArrayList<>();
        JSONObject json = JSONUtil.parseObj(resp.body());
        JSONArray result = json.getJSONArray("result");
        if (result == null) return shopIds;

        for (int i = 0; i < result.size(); i++) {
            JSONObject item = result.getJSONObject(i);
            JSONObject payload = item.getJSONObject("payload");
            if (payload != null) {
                shopIds.add(payload.getLong("shopId"));
            }
        }
        return shopIds;
    }
}
