package com.sqlong.demo.service;

import com.sqlong.framework.annotation.SqlongService;

@SqlongService
public class ShowService {

    public String add(String i) {
        return i + "| service |";
    }

}
