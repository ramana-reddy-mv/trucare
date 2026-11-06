package com.trucare.document.controller;

import com.trucare.document.service.DocumentService;
import com.trucare.document.utitly.FileUploadStatus;
import com.trucare.shared.document.DocumentDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @PostMapping(value = "/upload-doc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> upLoadDocument(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "memberId", required = false) String memberId,
            @RequestParam("documentType") String documentType
            ) throws IOException {
        return ResponseEntity.ok(documentService.uploadDocument(file, memberId, documentType));
    }

    @PostMapping(value = "/upLoadDocs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<FileUploadStatus>> upLoadDocuments(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "memberId", required = false) String memberId,
            @RequestParam("documentType") String documentType
    ) throws IOException {
        return ResponseEntity.ok(documentService.uploadDocuments(files, memberId, documentType));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDTO> getDocumentById(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentById(id));
    }

    @GetMapping("/claim/{claimId}")
    public ResponseEntity<List<DocumentDTO>> getDocumentsByClaimId(@PathVariable Long claimId) {
        return ResponseEntity.ok(documentService.getDocumentsByClaimId(claimId));
    }

    @GetMapping
    public ResponseEntity<List<DocumentDTO>> getAllDocuments() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

    @GetMapping("/validateDocs/{documentIds}")
    public ResponseEntity<List<Long>> validateDocuments(@PathVariable List<Long> documentIds ){
        return ResponseEntity.ok(documentService.validateDocumetns(documentIds));
    }


//    @PutMapping("/{id}")
//    public ResponseEntity<DocumentDTO> updateDocument(@PathVariable Long id, @RequestBody DocumentDTO documentDTO) {
//        return ResponseEntity.ok(documentService.updateDocument(id, documentDTO));
//    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
