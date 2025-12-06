/**
 * 全局配置
 */
const BASE_URL = '/api/v1'; //

// 配置 Axios 拦截器，每次请求自动携带 Token
axios.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
}, error => {
    return Promise.reject(error);
});

// 配置 Axios 响应拦截器，处理 401 过期
axios.interceptors.response.use(response => {
    return response;
}, error => {
    if (error.response && error.response.status === 401) {
        alert('登录已过期，请重新登录');
        logout();
    }
    return Promise.reject(error);
});

/**
 * 用户管理模块
 */

// 1. 用户注册
async function register(username, password, role, phone, email) {
    try {
        const res = await axios.post(`${BASE_URL}/user/register`, {
            username,
            password,
            role,
            phone,
            email
        });
        if (res.data.code === 200) {
            alert('注册成功，请登录');
            location.reload();
        } else {
            alert(res.data.message);
        }
    } catch (err) {
        alert('注册失败: ' + (err.response?.data?.message || err.message));
    }
}

// 2. 用户登录
async function login(username, password) {
    try {
        const res = await axios.post(`${BASE_URL}/user/login`, {
            username, password
        });
        if (res.data.code === 200) {
            const token = res.data.data.token;
            localStorage.setItem('token', token);
            window.location.href = 'dashboard.html';
        } else {
            alert(res.data.message);
        }
    } catch (err) {
        alert('登录失败: ' + (err.response?.data?.message || err.message));
    }
}

// 3. 获取用户信息
async function fetchUserInfo() {
    try {
        const res = await axios.get(`${BASE_URL}/user/info`);
        if (res.data.code === 200) {
            const user = res.data.data;
            document.getElementById('navUsername').innerText = user.username;
            document.getElementById('userId').innerText = user.userId;
            document.getElementById('userRole').innerText = user.role;
            document.getElementById('createdTime').innerText = user.createdTime;
        }
    } catch (err) {
        console.error("获取用户信息失败", err);
    }
}

function logout() {
    localStorage.removeItem('token');
    window.location.href = 'index.html';
}

function checkAuth() {
    if (!localStorage.getItem('token')) {
        window.location.href = 'index.html';
    }
}

/**
 * 文档管理模块
 */

// // 1. 单文件上传
// async function handleUpload() {
//     const fileInput = document.getElementById('fileInput');
//     const desc = document.getElementById('fileDesc').value;
//
//     if (fileInput.files.length === 0) {
//         alert('请选择文件');
//         return;
//     }
//
//     const formData = new FormData();
//     formData.append('file', fileInput.files[0]);
//     formData.append('description', desc);
//     // documentName 可选，不传则用原名
//
//     try {
//         const res = await axios.post(`${BASE_URL}/document/upload`, formData, {
//             headers: { 'Content-Type': 'multipart/form-data' }
//         });
//         if (res.data.code === 200) {
//             alert('上传成功');
//             // 关闭模态框并刷新列表
//             const modal = bootstrap.Modal.getInstance(document.getElementById('uploadModal'));
//             modal.hide();
//             fileInput.value = ''; // 清空选择
//             loadDocuments(1);
//         } else {
//             alert('上传失败: ' + res.data.message);
//         }
//     } catch (err) {
//         alert('上传出错: ' + (err.response?.data?.message || err.message));
//     }
// }

// 核心逻辑函数：根据文件数量调用不同接口
async function handleUpload() {
    const fileInput = document.getElementById('fileInput');
    const desc = document.getElementById('fileDesc').value;

    const files = fileInput.files;

    if (files.length === 0) {
        alert('请选择文件');
        return;
    }

    const submitBtn = document.querySelector('#uploadModal .btn-primary');
    submitBtn.disabled = true;

    let result = null;

    if (files.length === 1) {
        // --- 1. 单文件上传逻辑 ---
        submitBtn.textContent = '单文件上传中...';
        result = await uploadSingleFile(files[0], desc);

    } else {
        // --- 2. 批量文件上传逻辑 ---
        submitBtn.textContent = `批量上传中 (${files.length} 个文件)...`;
        result = await uploadBatchFiles(files, desc);
    }

    submitBtn.disabled = false;
    submitBtn.textContent = '开始上传';

    if (result && result.success) {
        alert(result.message);
        // 成功后：关闭模态框并刷新列表
        const modal = bootstrap.Modal.getInstance(document.getElementById('uploadModal'));
        modal.hide();
        fileInput.value = ''; // 清空选择
        loadDocuments(1);
    } else if (result) {
        alert(result.message);
    }
}

// 辅助函数：调用单文件上传接口
async function uploadSingleFile(file, description) {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('description', description);

    try {
        const res = await axios.post(`${BASE_URL}/document/upload`, formData, {
            headers: { 'Content-Type': 'multipart/form-data' }
        });

        if (res.data.code === 200) {
            return { success: true, message: `文件 ${file.name} 上传成功！` };
        } else {
            return { success: false, message: `文件 ${file.name} 上传失败: ${res.data.message}` };
        }
    } catch (err) {
        return { success: false, message: `文件 ${file.name} 上传出错: ${(err.response?.data?.message || err.message)}` };
    }
}

// 辅助函数：调用批量文件上传接口
async function uploadBatchFiles(files, description) {
    const formData = new FormData();
    formData.append('description', description);

    // 假设后端接口接收的字段名为 'files'
    for (let i = 0; i < files.length; i++) {
        formData.append('files', files[i]);
    }

    try {
        const res = await axios.post(`${BASE_URL}/document/batch-upload`, formData, {
            headers: { 'Content-Type': 'multipart/form-data' }
        });

        if (res.data.code === 200) {
            return { success: true, message: `批量上传成功！共处理 ${files.length} 个文件。` };
        } else {
            return { success: false, message: `批量上传失败: ${res.data.message || '未知错误'}` };
        }
    } catch (err) {
        return { success: false, message: '批量上传出错: ' + (err.response?.data?.message || err.message) };
    }
}

// 2. 查询文档列表
async function loadDocuments(pageNum) {
    const keyword = document.getElementById('searchInput').value;
    const pageSize = 10;

    try {
        const res = await axios.get(`${BASE_URL}/document/list`, {
            params: { pageNum, pageSize, keyword }
        });

        if (res.data.code === 200) {
            renderTable(res.data.data.list);
            renderPagination(res.data.data);
        }
    } catch (err) {
        console.error("加载列表失败", err);
    }
}

// 渲染表格
function renderTable(list) {
    const tbody = document.getElementById('docTableBody');
    tbody.innerHTML = '';

    if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center">暂无文档</td></tr>';
        return;
    }

    list.forEach(doc => {
        // 文件大小转换
        const sizeStr = (doc.fileSize / 1024).toFixed(2) + ' KB';

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${doc.documentName}</td>
            <td><span class="badge bg-secondary">${doc.fileType}</span></td>
            <td>${sizeStr}</td>
            <td>v${doc.currentVersion}</td>
            <td>${doc.uploadTime}</td>
            <td>
                <button class="btn btn-sm btn-info text-white" onclick="downloadDoc('${doc.documentId}')">下载</button>
                <button class="btn btn-sm btn-danger" onclick="deleteDoc('${doc.documentId}')">删除</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

// 渲染分页
function renderPagination(data) {
    const pagination = document.getElementById('pagination');
    pagination.innerHTML = '';

    // 简单实现：上一页、当前页、下一页
    const prevDisabled = data.pageNum === 1 ? 'disabled' : '';
    const nextDisabled = data.pageNum === data.pages ? 'disabled' : '';

    pagination.innerHTML = `
        <li class="page-item ${prevDisabled}">
            <a class="page-link" href="#" onclick="loadDocuments(${data.pageNum - 1})">上一页</a>
        </li>
        <li class="page-item active">
            <span class="page-link">第 ${data.pageNum} / ${data.pages} 页</span>
        </li>
        <li class="page-item ${nextDisabled}">
            <a class="page-link" href="#" onclick="loadDocuments(${data.pageNum + 1})">下一页</a>
        </li>
    `;
}

// 3. 下载文档
async function downloadDoc(documentId) {
    try {
        // 注意：下载文件需要设置 responseType 为 blob
        const res = await axios.get(`${BASE_URL}/document/download`, {
            params: { documentId },
            responseType: 'blob',
            withCredentials: true
        });

        let fileName = `document_${documentId}.file`;
        const headers = res.headers;

        // 方式1：解析 Content-Disposition 头（推荐）
        if (headers['content-disposition']) {
            const disposition = headers['content-disposition'];
            // 正则匹配 filename="xxx" 或 filename=xxx 格式
            const match = disposition.match(/filename=("?)([^"]+)\1/);
            if (match && match[2]) {
                // 解码中文文件名（对应后端的URLEncoder编码）
                fileName = decodeURIComponent(match[2]);
            }
        }
        // 方式2：解析自定义的 filename 头（备用）
        else if (headers['filename']) {
            fileName = decodeURIComponent(headers['filename']);
        }


        // 创建临时下载链接
        const url = window.URL.createObjectURL(new Blob([res.data]));
        const link = document.createElement('a');
        link.href = url;
        // 尝试从 header 获取文件名，或者使用默认值
        link.setAttribute('download', fileName);
        document.body.appendChild(link);
        link.click();

        // 释放资源 + 清理DOM
        setTimeout(() => {
            window.URL.revokeObjectURL(url);
            document.body.removeChild(link);
        }, 100);

    } catch (err) {
        console.error('下载失败：', err);
        // 解析后端返回的错误信息（跨域时需后端暴露头）
        let errMsg = '下载失败，请重试';
        if (err.response?.data instanceof Blob) {
            const reader = new FileReader();
            reader.onload = () => {
                alert('下载失败：' + reader.result);
            };
            reader.readAsText(err.response.data);
        } else {
            alert(errMsg);
        }
    }
}

// 4. 删除文档
async function deleteDoc(documentId) {
    if (!confirm('确定要删除该文档吗？')) return;

    try {
        const res = await axios.delete(`${BASE_URL}/document/${documentId}`);
        if (res.data.code === 200) {
            alert('删除成功');
            loadDocuments(1); // 刷新列表
        } else {
            alert(res.data.message);
        }
    } catch (err) {
        alert('删除失败');
    }
}