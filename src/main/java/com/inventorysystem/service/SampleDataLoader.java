package com.inventorysystem.service;

import com.inventorysystem.domain.Item;
import com.inventorysystem.excel.ExcelItemParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 새 세션의 초기 데이터로 쓸 예시 엑셀 파일(sample/inventory.xlsx)을 불러온다. */
@Component
public class SampleDataLoader {

    static final String SAMPLE_PATH = "sample/inventory.xlsx";
    static final String SAMPLE_FILE_NAME = "inventory.xlsx";

    private final ExcelItemParser parser;

    public SampleDataLoader(ExcelItemParser parser) {
        this.parser = parser;
    }

    public void loadInto(InventorySession session) {
        try (InputStream in = new ClassPathResource(SAMPLE_PATH).getInputStream()) {
            byte[] bytes = in.readAllBytes();
            List<Item> items = parser.parse(bytes);
            session.load(SAMPLE_FILE_NAME, bytes, items);
        } catch (IOException e) {
            throw new UncheckedIOException("예시 데이터 파일을 읽지 못했습니다.", e);
        }
    }
}
