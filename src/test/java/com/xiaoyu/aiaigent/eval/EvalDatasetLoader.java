package com.xiaoyu.aiaigent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

/**
 * 测试用：加载 classpath:eval/dataset.json 为 EvalDataset。
 */
public final class EvalDatasetLoader {

    private EvalDatasetLoader() {
    }

    public static EvalDataset load() {
        try (InputStream in = new ClassPathResource("eval/dataset.json").getInputStream()) {
            return new ObjectMapper().readValue(in, EvalDataset.class);
        } catch (Exception e) {
            throw new IllegalStateException("加载 eval/dataset.json 失败", e);
        }
    }
}
