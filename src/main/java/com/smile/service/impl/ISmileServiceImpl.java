package com.smile.service.impl;

import com.smile.annotation.SmileService;
import com.smile.service.ISmileService;

@SmileService
public class ISmileServiceImpl implements ISmileService {
    @Override
    public String get(String name) {
        return "My name is " + name;
    }
}
