package com.inventorysystem.web;

import com.inventorysystem.dto.ChangeResponse;
import com.inventorysystem.dto.ImportResponse;
import com.inventorysystem.dto.ItemListResponse;
import com.inventorysystem.dto.SaveResponse;
import com.inventorysystem.dto.StockChangeRequest;
import com.inventorysystem.exception.InvalidRequestException;
import com.inventorysystem.service.ItemService;
import com.inventorysystem.service.ItemService.ExportedFile;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/items")
public class ItemApiController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ItemService itemService;

    public ItemApiController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public ItemListResponse list() {
        return itemService.list();
    }

    @PostMapping("/{id}/inbound")
    public ChangeResponse inbound(@PathVariable long id, @RequestBody StockChangeRequest request) {
        return itemService.inbound(id, requireQuantity(request));
    }

    @PostMapping("/{id}/outbound")
    public ChangeResponse outbound(@PathVariable long id, @RequestBody StockChangeRequest request) {
        return itemService.outbound(id, requireQuantity(request));
    }

    @PostMapping("/save")
    public SaveResponse save() {
        return itemService.save();
    }

    @PostMapping("/discard")
    public ItemListResponse discard() {
        return itemService.discard();
    }

    @PostMapping("/upload")
    public ImportResponse upload(@RequestPart("file") MultipartFile file) {
        return itemService.upload(file);
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download() {
        ExportedFile file = itemService.export();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.bytes());
    }

    private long requireQuantity(StockChangeRequest request) {
        if (request == null) {
            throw new InvalidRequestException("수량은 0 이상 999,999,999,999,999 이하의 정수여야 합니다.");
        }
        return request.toQuantity();
    }
}
