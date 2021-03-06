package com.baoshine.questionnaire.pool.utils;

import cn.hutool.core.date.DateUtil;
import com.baoshine.questionnaire.utils.JsonUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class TestUtils {

    public static void prettyPrintJson(Object object, String path) throws IOException {
        String json = doPrettyJson(object);
        File file = new File(path);
        OutputStreamWriter write = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8);
        BufferedWriter writ = new BufferedWriter(write);
        writ.write(json);
        writ.close();
    }

    public static void prettyPrintJson(Object object) throws JsonProcessingException {
        String json = doPrettyJson(object);
        System.out.println(json);
    }

    private static String doPrettyJson(Object object) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        // 反序列化时忽略未知属性
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 序列化时忽略空值
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        SimpleModule module = new SimpleModule();
        module.addSerializer(Date.class, new JsonSerializer<Date>() {
            @Override
            public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(DateUtil.format(value, "yyyy-MM-dd HH:mm:ss.SSS"));
            }
        });
        JsonUtils.instance().registerModule(module);
        return JsonUtils.instance().writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }
}
