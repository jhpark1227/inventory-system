package com.inventorysystem.service;

import com.inventorysystem.domain.Item;
import com.inventorysystem.dto.ChangeResponse;
import com.inventorysystem.dto.ImportResponse;
import com.inventorysystem.dto.ItemListResponse;
import com.inventorysystem.dto.ItemResponse;
import com.inventorysystem.dto.SaveResponse;
import com.inventorysystem.exception.InvalidRequestException;
import com.inventorysystem.excel.ExcelExporter;
import com.inventorysystem.excel.ExcelItemParser;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ItemService {

    private static final DateTimeFormatter DOWNLOAD_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final InventorySession session;
    private final SampleDataLoader sampleDataLoader;
    private final ExcelItemParser parser;
    private final ExcelExporter exporter;

    public ItemService(InventorySession session, SampleDataLoader sampleDataLoader,
            ExcelItemParser parser, ExcelExporter exporter) {
        this.session = session;
        this.sampleDataLoader = sampleDataLoader;
        this.parser = parser;
        this.exporter = exporter;
    }

    public ItemListResponse list() {
        ensureLoaded();
        return new ItemListResponse(session.getFileName(), session.isDirty(), session.changedCount(),
                session.isUnexported(), itemResponses());
    }

    public ChangeResponse inbound(long id, long quantity) {
        ensureLoaded();
        return change(session.inbound(id, quantity));
    }

    public ChangeResponse outbound(long id, long quantity) {
        ensureLoaded();
        return change(session.outbound(id, quantity));
    }

    public SaveResponse save() {
        ensureLoaded();
        int saved = session.save(LocalDateTime.now());
        return new SaveResponse(saved, session.getFileName(), session.isDirty(), session.changedCount(),
                session.isUnexported(), itemResponses());
    }

    public ItemListResponse discard() {
        ensureLoaded();
        session.discard();
        return list();
    }

    public ImportResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("첨부된 파일이 없습니다.");
        }
        String fileName = baseName(file.getOriginalFilename());
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new InvalidRequestException("엑셀 파일(.xlsx)만 첨부할 수 있습니다.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidRequestException("파일을 읽지 못했습니다. 다시 시도해 주세요.");
        }
        List<Item> items = parser.parse(bytes);
        session.load(fileName, bytes, items);
        return new ImportResponse(items.size(), session.getFileName(), session.isDirty(),
                session.changedCount(), session.isUnexported(), itemResponses());
    }

    /** 저장본 기준의 엑셀 파일을 만든다. 작업본(저장하지 않은 변경)은 포함되지 않는다. */
    public ExportedFile export() {
        ensureLoaded();
        byte[] bytes = exporter.export(session.getOriginalBytes(), session.getItems());
        session.markExported();
        String stem = session.getFileName().replaceFirst("(?i)\\.xlsx$", "");
        String name = stem + "_수정_" + LocalDateTime.now().format(DOWNLOAD_TIME) + ".xlsx";
        return new ExportedFile(name, bytes);
    }

    public record ExportedFile(String fileName, byte[] bytes) {
    }

    private ChangeResponse change(Item item) {
        return new ChangeResponse(ItemResponse.from(item), session.isDirty(), session.changedCount(),
                session.isUnexported());
    }

    private List<ItemResponse> itemResponses() {
        return session.getItems().stream().map(ItemResponse::from).toList();
    }

    private void ensureLoaded() {
        if (!session.isLoaded()) {
            sampleDataLoader.loadInto(session);
        }
    }

    /** 경로가 섞여 들어온 파일명에서 마지막 이름만 남긴다. */
    private static String baseName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String normalized = name.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }
}
