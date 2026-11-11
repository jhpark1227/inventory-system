package com.inventorysystem.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 화면 골격만 응답한다. 데이터는 화면이 {@code /api/items}를 호출해서 채운다. */
@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }
}
