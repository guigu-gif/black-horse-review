package com.hmdp.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 调用 DeepSeek Embedding API，把文字转成向量
 */
@Slf4j
@Service
public class EmbeddingService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.embedding-url}")
    private String embeddingUrl;

    @Value("${deepseek.embedding-model}")
    private String embeddingModel;

    /**
     * 把一段文字转成向量（float 列表）
     * @param text 要转换的文字
     * @return 向量，例如 [0.1, -0.3, 0.5, ...]
     */
    public List<Float> embed(String text) {
        // 构造请求体
        JSONObject body = new JSONObject();
        body.set("model", embeddingModel);
        body.set("input", text);

        // 发 HTTP 请求
        String response = HttpRequest.post(embeddingUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(30000)
                .execute()
                .body();

        // 打印原始响应，方便排查问题
        log.info("DeepSeek 原始响应: {}", response);

        // 解析响应，取 data[0].embedding
        JSONObject json = JSONUtil.parseObj(response);
        JSONArray dataArray = json.getJSONArray("data");
        if (dataArray == null || dataArray.isEmpty()) {
            log.error("Embedding API 返回异常: {}", response);
            throw new RuntimeException("Embedding 失败，响应: " + response);
        }
        JSONArray embeddingArray = dataArray.getJSONObject(0).getJSONArray("embedding");

        List<Float> vector = new ArrayList<>();
        for (int i = 0; i < embeddingArray.size(); i++) {
            vector.add(embeddingArray.getFloat(i));
        }
        return vector;
    }
}
