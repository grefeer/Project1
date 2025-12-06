package com.aiqa.project1.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Service
public class DocsServiceimpl {
    public String updateSingleDocument(String path, String username, MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        file.transferTo(new File(path + "/" + username + "/" + fileName));
        return fileName;
    }
}
