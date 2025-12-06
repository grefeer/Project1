package com.aiqa.project1.service;

import com.aiqa.project1.pojo.AuthInfo;
import com.aiqa.project1.pojo.Result;
import com.aiqa.project1.utils.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface DocsService {
    /**
     * 单文件上传
     * @param file
     * @param description
     * @param userId
     * @return
     * @throws BusinessException
     */
    Result uploadSingleDocument(MultipartFile file, String description, String userId) throws BusinessException;


    /**
     * 多文件上传
     * @param files
     * @param userId
     * @return
     * @throws BusinessException
     */
    Result uploadMultiDocuments(List<MultipartFile> files, String userId) throws BusinessException;

    /**
     * 查询单个文档信息
     * @param documentId
     * @param authInfo
     * @param version
     * @return
     * @throws BusinessException
     */
    Result getSingleDocument(String documentId, AuthInfo authInfo, Long version) throws BusinessException;

    /**
     * 删除单个文件
     * @param documentId
     * @param request
     * @return
     */
    Result deleteSingleDocument(String documentId, HttpServletRequest request);

    /**
     * 下载单个文件
     * @param documentId
     * @param version
     * @param request
     * @param response
     * @return
     */
    Result downloadSingleDocument(String documentId, Long version, HttpServletRequest request, HttpServletResponse response);

    /**
     * 查询用户文档列表
     * @param pageNum
     * @param pageSize
     * @param keyword
     * @param authInfo
     * @return
     */
    Result requestDocumentList(Integer pageNum, Integer pageSize, String keyword, AuthInfo authInfo);

    }
