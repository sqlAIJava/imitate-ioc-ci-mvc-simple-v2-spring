package com.sqlong.demo.controller;

import com.sqlong.demo.service.ShowService;
import com.sqlong.framework.annotation.SqlongAutowired;
import com.sqlong.framework.annotation.SqlongController;
import com.sqlong.framework.annotation.SqlongRequestMapping;
import com.sqlong.framework.annotation.SqlongRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@SqlongController
@SqlongRequestMapping("/show")
public class ShowController {

    @SqlongAutowired
    private ShowService showService;

    @SqlongRequestMapping("/add")
    public void add(HttpServletRequest request, HttpServletResponse response, @SqlongRequestParam("sp") String sp) throws IOException {
        System.out.println("接收到的sp: " + sp);
        String r = showService.add(sp);
        System.out.println("处理后的sp：" + r);
        r += "| controller |";
        response.getWriter().write(r);
    }

}
