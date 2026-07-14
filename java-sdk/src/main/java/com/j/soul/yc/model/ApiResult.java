package com.j.soul.yc.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 上游业务 JSON 通用响应。
 * <p>
 * {@code code==200} 通常表示业务成功；非 200 为业务拒绝（仍返回本对象，不抛异常）。
 * {@code msg} 兼容上游字段 {@code message}。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiResult {
    /** 业务状态码，200 常为成功 */
    private Integer code;

    /** 提示信息（兼容 message） */
    @JsonAlias("message")
    private String msg;

    /** 业务数据；登录成功时可能含 access_token 等 */
    private JsonNode data;

    public ApiResult() {
    }

    public ApiResult(Integer code, String msg, JsonNode data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
    }
}
