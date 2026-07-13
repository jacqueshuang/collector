package com.j.soul.yc.model;

import com.fasterxml.jackson.databind.JsonNode;

public class ApiResult {
    private Integer code;
    private String msg;
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
