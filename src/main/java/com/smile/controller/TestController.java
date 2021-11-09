package com.smile.controller;

import com.smile.annotation.SmileAutowired;
import com.smile.annotation.SmileController;
import com.smile.annotation.SmileRequestMapping;
import com.smile.annotation.SmileRequestParam;
import com.smile.service.ISmileService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@SmileController
@SmileRequestMapping("/demo")
public class TestController {
    @SmileAutowired
    private ISmileService smileService;

    @SmileRequestMapping("/query")
    public String query(HttpServletRequest request, HttpServletResponse response,@SmileRequestParam("name") String name) {
        String result = smileService.get(name);
        try {
            response.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }
}
