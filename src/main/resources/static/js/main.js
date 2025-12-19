/**
 * 1. 全局配置与拦截器
 */
const BASE_URL = '/api/v1';

axios.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    if (token) config.headers['Authorization'] = `Bearer ${token}`;
    return config;
}, error => Promise.reject(error));

axios.interceptors.response.use(
    response => response,
    error => {
        if (error.response && error.response.status === 401) {
            if (!window.location.pathname.endsWith('index.html')) {
                localStorage.clear();
                window.location.href = 'index.html';
            }
        }
        return Promise.reject(error);
    }
);

/**
 * 2. 状态管理
 */
let sessions = [];
let nextSessionId = 0;
let currentSessionId = null;
let currentPage = 1;
let currentSearchKeyword = ""; // 搜索关键词状态

/**
 * 3. 页面初始化
 */
window.onload = () => {
    const isLoginPath = window.location.pathname.endsWith('index.html') || window.location.pathname === '/';
    if (isLoginPath) {
        if (localStorage.getItem('token')) location.href = 'dashboard.html';
    } else {
        fetchUserInfo();
        loadDocuments(1); // 初始化加载
        initDragAndDrop();
        createNewChat();
    }
};

/**
 * 4. 认证模块
 */
async function login(username, password) {
    try {
        const res = await axios.post(`${BASE_URL}/user/login`, { username, password });
        if (res.data.code === 200) {
            localStorage.setItem('token', res.data.data.token);
            location.href = 'dashboard.html';
        } else { alert(res.data.message); }
    } catch (e) { alert('登录失败'); }
}

function logout() {
    localStorage.clear();
    location.href = 'index.html';
}

/**
 * 5. 文档管理 (核心功能：搜索、分页、渲染修复)
 */

// 触发搜索函数
function handleSearch() {
    currentSearchKeyword = document.getElementById('searchInput').value.trim();
    loadDocuments(1); // 搜索从第一页开始
}

async function loadDocuments(page = 1) {
    currentPage = page;
    try {
        // 合并搜索关键词到请求参数
        const res = await axios.get(`${BASE_URL}/document/list`, {
            params: {
                page: page,
                pageSize: 10,
                keyword: currentSearchKeyword // 增加搜索参数
            }
        });

        if (res.data.code === 200) {
            const list = res.data.data.list || [];
            const total = res.data.data.total || 0;
            renderDocs(list);
            renderPagination(total, page);
        }
    } catch (e) { console.error("加载列表失败", e); }
}

// 核心逻辑函数：根据文件数量调用不同接口
async function handleUpload(externalFiles = null) {
    const fileInput = document.getElementById('fileInput');
    const desc = document.getElementById('fileDesc').value;

    // 优先使用外部传入的文件（如拖拽的文件），否则使用文件选择框的文件
    const files = externalFiles || fileInput.files;

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
        await loadDocuments(1);
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

function renderDocs(docs) {
    const tbody = document.getElementById('docTableBody');
    if (!tbody) return;

    if (docs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">未找到相关文档</td></tr>';
        return;
    }

    // 【重要修复】如果字段不显示，请尝试将 doc.fileName 替换为 doc.name
    tbody.innerHTML = docs.map(doc => `
        <tr>
            <td><i class="bi bi-file-earmark-pdf me-2 text-danger"></i>${doc.documentName  || '未命名文档'}</td>
            <td><span class="badge bg-secondary">v${doc.version || 1}</span></td>
            <td>${doc.fileSize ? (doc.fileSize / 1024).toFixed(1) + ' KB' : '--'}</td>
            <td>${doc.uploadTime || '--'}</td>
            <td>
                <button class="btn btn-sm btn-link" onclick="downloadDoc('${doc.documentId}')">下载</button>
                <button class="btn btn-sm btn-link text-danger" onclick="deleteDoc('${doc.documentId}')">删除</button>
            </td>
        </tr>
    `).join('');
}

// 分页渲染逻辑
function renderPagination(total, activePage) {
    const totalPages = Math.ceil(total / 10);
    const container = document.getElementById('paginationContainer');
    if (!container) return;

    let html = `<li class="page-item ${activePage === 1 ? 'disabled' : ''}">
                    <a class="page-link" href="#" onclick="loadDocuments(${activePage - 1})">上一页</a>
                </li>`;

    for (let i = 1; i <= totalPages; i++) {
        html += `<li class="page-item ${i === activePage ? 'active' : ''}">
                    <a class="page-link" href="#" onclick="loadDocuments(${i})">${i}</a>
                 </li>`;
    }

    html += `<li class="page-item ${activePage === totalPages ? 'disabled' : ''}">
                <a class="page-link" href="#" onclick="loadDocuments(${activePage + 1})">下一页</a>
             </li>`;

    container.innerHTML = html;
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
            // 优先匹配 filename* (UTF-8 编码版本)
            const utf8Match = headers['content-disposition'].match(/filename\*=utf-8''([^;]+)/i);
            if (utf8Match) {
                fileName = decodeURIComponent(utf8Match[1]);
            } else {
                // 备选匹配普通 filename
                const normalMatch = headers['content-disposition'].match(/filename="?([^";]+)"?/i);
                if (normalMatch) fileName = decodeURIComponent(normalMatch[1]);
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
        const res = await axios.delete(`${BASE_URL}/document/delete/${documentId}`);
        if (res.data.code === 200) {
            alert('删除成功');
            await loadDocuments(1); // 刷新列表
        } else {
            alert(res.data.message);
        }
    } catch (err) {
        alert('删除失败');
    }
}

/**
 * 6. 问答模块 (Session & Polling)
 */
function createNewChat() {
    const sid = nextSessionId++;
    const newSession = { id: sid, title: `新会话 ${sid}`, messages: [] };
    sessions.push(newSession);
    switchSession(sid);
}

function switchSession(sid) {
    currentSessionId = sid;
    document.getElementById('docs-section').style.display = 'none';
    document.getElementById('chat-section').style.display = 'flex';
    document.getElementById('nav-docs').classList.remove('active');
    renderSessionList();
    renderMessages(sid);
}

function switchToDocs() {
    document.getElementById('chat-section').style.display = 'none';
    document.getElementById('docs-section').style.display = 'block';
    document.getElementById('nav-docs').classList.add('active');
    currentSessionId = null;
    loadDocuments(1);
    renderSessionList();
}

function renderSessionList() {
    const container = document.getElementById('sessionListContainer');
    if (!container) return;
    container.innerHTML = [...sessions].reverse().map(s => `
        <div class="nav-link-custom ${s.id === currentSessionId ? 'active' : ''}" onclick="switchSession(${s.id})">
            <i class="bi bi-chat-left-dots me-2"></i> ${s.title}
        </div>
    `).join('');
}

async function handleSendMessage() {
    const input = document.getElementById('questionInput');
    const question = input.value.trim();
    if (!question || currentSessionId === null) return;

    appendMessageUI('user', question, true);
    input.value = '';

    try {
        const res = await axios.post(`${BASE_URL}/qa/ask`,
            { question, sessionId: currentSessionId });
        if (res.data.code === 200) startPolling(currentSessionId);
    } catch (e) { appendMessageUI('ai', '请求异常', true); }
}

function startPolling(sid) {
    const aiMsgObj = appendMessageUI('ai', 'AI 正在分析...', true);
    setTimeout(() => {
        const timer = setInterval(async () => {
            try {
                const res = await axios.get(`${BASE_URL}/qa/status`,
                    { params: { sessionId: sid } });
                const answer = res.data.data.answer || "...";
                if (currentSessionId === sid) {
                    aiMsgObj.element.innerText = answer.replace("<最终回答>", "").replace("<AI回答>", "");
                    document.getElementById('message-area').scrollTop = 99999;
                }
                aiMsgObj.text = answer;
                if (res.data.code === 200 || answer.includes("<最终回答>")) clearInterval(timer);
            } catch (err) { clearInterval(timer); }
        }, 2000);
    }, 5000);
}


async function fetchUserInfo() {
    const res = await axios.get(`${BASE_URL}/user/info`);
    document.getElementById('navUsername').innerText = res.data.data.username;
}

function appendMessageUI(role, text, shouldSave) {
    const area = document.getElementById('message-area');
    const row = document.createElement('div');
    row.className = `msg-row ${role === 'user' ? 'user-row' : 'ai-row'}`;
    row.innerHTML = `<div class="msg-content shadow-sm"><div class="msg-text">${text}</div></div>`;
    area.appendChild(row);
    area.scrollTop = area.scrollHeight;
    const msgObj = { role, text, element: row.querySelector('.msg-text') };
    if (shouldSave) {
        const s = sessions.find(x => x.id === currentSessionId);
        if (s) s.messages.push(msgObj);
    }
    return msgObj;
}

function renderMessages(sid) {
    const area = document.getElementById('message-area');
    area.innerHTML = '';
    const s = sessions.find(x => x.id === sid);
    if (s) s.messages.forEach(m => appendMessageUI(m.role, m.text, false));
}

function initDragAndDrop() {
    const zone = document.getElementById('dropZone');
    if (!zone) return;
    ['dragover', 'drop'].forEach(ev => zone.addEventListener(ev, (e) => {
        e.preventDefault();
        if (ev === 'dragover') zone.classList.add('dragover');
        else {
            zone.classList.remove('dragover');
            handleUpload(e.dataTransfer.files);
        }
    }));
}



// /**
//  * 全局配置
//  */
// const BASE_URL = '/api/v1'; //
//
// // 配置 Axios 拦截器，每次请求自动携带 Token
// axios.interceptors.request.use(config => {
//     const token = localStorage.getItem('token');
//     if (token) {
//         config.headers['Authorization'] = `Bearer ${token}`;
//     }
//     return config;
// }, error => {
//     return Promise.reject(error);
// });
//
// // 配置 Axios 响应拦截器，处理 401 过期
// axios.interceptors.response.use(response => {
//     return response;
// }, error => {
//     if (error.response && error.response.status === 401) {
//         alert('登录已过期，请重新登录');
//         logout();
//     }
//     return Promise.reject(error);
// });
//
// /**
//  * 用户管理模块
//  */
//
// // 1. 用户注册
// async function register(username, password, role, phone, email) {
//     try {
//         const res = await axios.post(`${BASE_URL}/user/register`, {
//             username,
//             password,
//             role,
//             phone,
//             email
//         });
//         if (res.data.code === 200) {
//             alert('注册成功，请登录');
//             location.reload();
//         } else {
//             alert(res.data.message);
//         }
//     } catch (err) {
//         alert('注册失败: ' + (err.response?.data?.message || err.message));
//     }
// }
//
// // 2. 用户登录
// async function login(username, password) {
//     try {
//         const res = await axios.post(`${BASE_URL}/user/login`, {
//             username, password
//         });
//         if (res.data.code === 200) {
//             const token = res.data.data.token;
//             localStorage.setItem('token', token);
//             window.location.href = 'dashboard.html';
//         } else {
//             alert(res.data.message);
//         }
//     } catch (err) {
//         alert('登录失败: ' + (err.response?.data?.message || err.message));
//     }
// }
//
// // 3. 获取用户信息
// async function fetchUserInfo() {
//     try {
//         const res = await axios.get(`${BASE_URL}/user/info`);
//         if (res.data.code === 200) {
//             const user = res.data.data;
//             document.getElementById('navUsername').innerText = user.username;
//             document.getElementById('userId').innerText = user.userId;
//             document.getElementById('userRole').innerText = user.role;
//             document.getElementById('createdTime').innerText = user.createdTime;
//         }
//     } catch (err) {
//         console.error("获取用户信息失败", err);
//     }
// }
//
// function logout() {
//     localStorage.removeItem('token');
//     window.location.href = 'index.html';
// }
//
// function checkAuth() {
//     if (!localStorage.getItem('token')) {
//         window.location.href = 'index.html';
//     }
// }
//
// /**
//  * 文档管理模块
//  */
//
// // // 1. 单文件上传
// // async function handleUpload() {
// //     const fileInput = document.getElementById('fileInput');
// //     const desc = document.getElementById('fileDesc').value;
// //
// //     if (fileInput.files.length === 0) {
// //         alert('请选择文件');
// //         return;
// //     }
// //
// //     const formData = new FormData();
// //     formData.append('file', fileInput.files[0]);
// //     formData.append('description', desc);
// //     // documentName 可选，不传则用原名
// //
// //     try {
// //         const res = await axios.post(`${BASE_URL}/document/upload`, formData, {
// //             headers: { 'Content-Type': 'multipart/form-data' }
// //         });
// //         if (res.data.code === 200) {
// //             alert('上传成功');
// //             // 关闭模态框并刷新列表
// //             const modal = bootstrap.Modal.getInstance(document.getElementById('uploadModal'));
// //             modal.hide();
// //             fileInput.value = ''; // 清空选择
// //             loadDocuments(1);
// //         } else {
// //             alert('上传失败: ' + res.data.message);
// //         }
// //     } catch (err) {
// //         alert('上传出错: ' + (err.response?.data?.message || err.message));
// //     }
// // }
//
// // 核心逻辑函数：根据文件数量调用不同接口
// async function handleUpload() {
//     const fileInput = document.getElementById('fileInput');
//     const desc = document.getElementById('fileDesc').value;
//
//     const files = fileInput.files;
//
//     if (files.length === 0) {
//         alert('请选择文件');
//         return;
//     }
//
//     const submitBtn = document.querySelector('#uploadModal .btn-primary');
//     submitBtn.disabled = true;
//
//     let result = null;
//
//     if (files.length === 1) {
//         // --- 1. 单文件上传逻辑 ---
//         submitBtn.textContent = '单文件上传中...';
//         result = await uploadSingleFile(files[0], desc);
//
//     } else {
//         // --- 2. 批量文件上传逻辑 ---
//         submitBtn.textContent = `批量上传中 (${files.length} 个文件)...`;
//         result = await uploadBatchFiles(files, desc);
//     }
//
//     submitBtn.disabled = false;
//     submitBtn.textContent = '开始上传';
//
//     if (result && result.success) {
//         alert(result.message);
//         // 成功后：关闭模态框并刷新列表
//         const modal = bootstrap.Modal.getInstance(document.getElementById('uploadModal'));
//         modal.hide();
//         fileInput.value = ''; // 清空选择
//         loadDocuments(1);
//     } else if (result) {
//         alert(result.message);
//     }
// }

// // 辅助函数：调用单文件上传接口
// async function uploadSingleFile(file, description) {
//     const formData = new FormData();
//     formData.append('file', file);
//     formData.append('description', description);
//
//     try {
//         const res = await axios.post(`${BASE_URL}/document/upload`, formData, {
//             headers: { 'Content-Type': 'multipart/form-data' }
//         });
//
//         if (res.data.code === 200) {
//             return { success: true, message: `文件 ${file.name} 上传成功！` };
//         } else {
//             return { success: false, message: `文件 ${file.name} 上传失败: ${res.data.message}` };
//         }
//     } catch (err) {
//         return { success: false, message: `文件 ${file.name} 上传出错: ${(err.response?.data?.message || err.message)}` };
//     }
// }

// // 辅助函数：调用批量文件上传接口
// async function uploadBatchFiles(files, description) {
//     const formData = new FormData();
//     formData.append('description', description);
//
//     // 假设后端接口接收的字段名为 'files'
//     for (let i = 0; i < files.length; i++) {
//         formData.append('files', files[i]);
//     }
//
//     try {
//         const res = await axios.post(`${BASE_URL}/document/batch-upload`, formData, {
//             headers: { 'Content-Type': 'multipart/form-data' }
//         });
//
//         if (res.data.code === 200) {
//             return { success: true, message: `批量上传成功！共处理 ${files.length} 个文件。` };
//         } else {
//             return { success: false, message: `批量上传失败: ${res.data.message || '未知错误'}` };
//         }
//     } catch (err) {
//         return { success: false, message: '批量上传出错: ' + (err.response?.data?.message || err.message) };
//     }
// }
//
// // 2. 查询文档列表
// async function loadDocuments(pageNum) {
//     const keyword = document.getElementById('searchInput').value;
//     const pageSize = 10;
//
//     try {
//         const res = await axios.get(`${BASE_URL}/document/list`, {
//             params: { pageNum, pageSize, keyword }
//         });
//
//         if (res.data.code === 200) {
//             renderTable(res.data.data.list);
//             renderPagination(res.data.data);
//         }
//     } catch (err) {
//         console.error("加载列表失败", err);
//     }
// }
//
// // 渲染表格
// function renderTable(list) {
//     const tbody = document.getElementById('docTableBody');
//     tbody.innerHTML = '';
//
//     if (!list || list.length === 0) {
//         tbody.innerHTML = '<tr><td colspan="6" class="text-center">暂无文档</td></tr>';
//         return;
//     }
//
//     list.forEach(doc => {
//         // 文件大小转换
//         const sizeStr = (doc.fileSize / 1024).toFixed(2) + ' KB';
//
//         const tr = document.createElement('tr');
//         tr.innerHTML = `
//             <td>${doc.documentName}</td>
//             <td><span class="badge bg-secondary">${doc.fileType}</span></td>
//             <td>${sizeStr}</td>
//             <td>v${doc.currentVersion}</td>
//             <td>${doc.uploadTime}</td>
//             <td>
//                 <button class="btn btn-sm btn-info text-white" onclick="downloadDoc('${doc.documentId}')">下载</button>
//                 <button class="btn btn-sm btn-danger" onclick="deleteDoc('${doc.documentId}')">删除</button>
//             </td>
//         `;
//         tbody.appendChild(tr);
//     });
// }
//
//
// // 渲染分页
// function renderPagination(data) {
//     const pagination = document.getElementById('pagination');
//     pagination.innerHTML = '';
//
//     // 简单实现：上一页、当前页、下一页
//     const prevDisabled = data.pageNum === 1 ? 'disabled' : '';
//     const nextDisabled = data.pageNum === data.pages ? 'disabled' : '';
//
//     pagination.innerHTML = `
//         <li class="page-item ${prevDisabled}">
//             <a class="page-link" href="#" onclick="loadDocuments(${data.pageNum - 1})">上一页</a>
//         </li>
//         <li class="page-item active">
//             <span class="page-link">第 ${data.pageNum} / ${data.pages} 页</span>
//         </li>
//         <li class="page-item ${nextDisabled}">
//             <a class="page-link" href="#" onclick="loadDocuments(${data.pageNum + 1})">下一页</a>
//         </li>
//     `;
// }
//
// // 3. 下载文档
// async function downloadDoc(documentId) {
//     try {
//         // 注意：下载文件需要设置 responseType 为 blob
//         const res = await axios.get(`${BASE_URL}/document/download`, {
//             params: { documentId },
//             responseType: 'blob',
//             withCredentials: true
//         });
//
//         let fileName = `document_${documentId}.file`;
//         const headers = res.headers;
//
//         // 方式1：解析 Content-Disposition 头（推荐）
//         if (headers['content-disposition']) {
//             const disposition = headers['content-disposition'];
//             // 正则匹配 filename="xxx" 或 filename=xxx 格式
//             const match = disposition.match(/filename=("?)([^"]+)\1/);
//             if (match && match[2]) {
//                 // 解码中文文件名（对应后端的URLEncoder编码）
//                 fileName = decodeURIComponent(match[2]);
//             }
//         }
//         // 方式2：解析自定义的 filename 头（备用）
//         else if (headers['filename']) {
//             fileName = decodeURIComponent(headers['filename']);
//         }
//
//
//         // 创建临时下载链接
//         const url = window.URL.createObjectURL(new Blob([res.data]));
//         const link = document.createElement('a');
//         link.href = url;
//         // 尝试从 header 获取文件名，或者使用默认值
//         link.setAttribute('download', fileName);
//         document.body.appendChild(link);
//         link.click();
//
//         // 释放资源 + 清理DOM
//         setTimeout(() => {
//             window.URL.revokeObjectURL(url);
//             document.body.removeChild(link);
//         }, 100);
//
//     } catch (err) {
//         console.error('下载失败：', err);
//         // 解析后端返回的错误信息（跨域时需后端暴露头）
//         let errMsg = '下载失败，请重试';
//         if (err.response?.data instanceof Blob) {
//             const reader = new FileReader();
//             reader.onload = () => {
//                 alert('下载失败：' + reader.result);
//             };
//             reader.readAsText(err.response.data);
//         } else {
//             alert(errMsg);
//         }
//     }
// }
//
// // 4. 删除文档
// async function deleteDoc(documentId) {
//     if (!confirm('确定要删除该文档吗？')) return;
//
//     try {
//         const res = await axios.delete(`${BASE_URL}/document/${documentId}`);
//         if (res.data.code === 200) {
//             alert('删除成功');
//             loadDocuments(1); // 刷新列表
//         } else {
//             alert(res.data.message);
//         }
//     } catch (err) {
//         alert('删除失败');
//     }
// }
//
// /**
//  * 会话与状态管理
//  */
// let sessions = []; // 存储结构: [{ id: 0, title: '会话0', messages: [] }]
// let currentSessionId = null;
// let nextSessionId = 0; // 从0开始计数
//
// // 页面加载初始化
// window.onload = () => {
//     checkAuth();
//     fetchUserInfo();
//     loadDocuments(1);
//     initDragAndDrop();
//     // 默认创建一个初始会话
//     createNewChat();
// };
//
// /**
//  * 会话逻辑
//  */
// function createNewChat() {
//     const sid = nextSessionId++;
//     const newSession = {
//         id: sid,
//         title: `新对话 ${sid}`,
//         messages: [] // 存储历史消息用于切换回来看
//     };
//     sessions.push(newSession);
//     renderSessionList();
//     switchSession(sid);
// }
//
// function renderSessionList() {
//     const container = document.getElementById('sessionListContainer');
//     container.innerHTML = '';
//
//     // 倒序显示，最新的在上面
//     [...sessions].reverse().forEach(session => {
//         const div = document.createElement('div');
//         div.className = `nav-item-custom ${session.id === currentSessionId ? 'active' : ''}`;
//         div.onclick = () => switchSession(session.id);
//         div.innerHTML = `<i class="bi bi-chat-left"></i> ${session.title}`;
//         container.appendChild(div);
//     });
// }
//
// function switchSession(sid) {
//     currentSessionId = sid;
//     // UI 切换
//     document.getElementById('docs-section').classList.remove('active');
//     document.getElementById('chat-section').classList.add('active');
//     document.getElementById('nav-docs').classList.remove('active');
//
//     renderSessionList();
//
//     // 重新渲染聊天区域内容
//     const area = document.getElementById('message-area');
//     area.innerHTML = '';
//     const session = sessions.find(s => s.id === sid);
//
//     if (session.messages.length === 0) {
//         area.innerHTML = `<div class="text-center mt-5 text-muted"><h5>我是您的 AI 助理</h5><p>请上传文档或直接向我提问</p></div>`;
//     } else {
//         session.messages.forEach(msg => {
//             appendMessageUI(msg.role, msg.text, false); // 不再重复保存
//         });
//     }
// }
//
// function switchToDocs() {
//     document.getElementById('chat-section').classList.remove('active');
//     document.getElementById('docs-section').classList.add('active');
//     document.getElementById('nav-docs').classList.add('active');
//     currentSessionId = null;
//     renderSessionList();
// }
//
// /**
//  * 拖拽上传逻辑 (调用文档上传接口)
//  */
// function initDragAndDrop() {
//     const dropZone = document.getElementById('dropZone');
//     const input = document.getElementById('questionInput');
//
//     ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eName => {
//         dropZone.addEventListener(eName, e => { e.preventDefault(); e.stopPropagation(); });
//     });
//
//     dropZone.addEventListener('dragover', () => dropZone.classList.add('dragover'));
//     dropZone.addEventListener('dragleave', () => dropZone.classList.remove('dragover'));
//
//     dropZone.addEventListener('drop', async (e) => {
//         dropZone.classList.remove('dragover');
//         const files = Array.from(e.dataTransfer.files);
//         if (files.length > 0) {
//             // 在输入框上方显示待上传标识
//             document.getElementById('filePreviewArea').innerHTML = files.map(f =>
//                 `<span class="badge bg-light text-dark border me-1"><i class="bi bi-file-earmark"></i> ${f.name}</span>`
//             ).join('');
//
//             // 执行上传
//             appendMessageUI('system', `正在上传 ${files.length} 个文件...`, true);
//             await handleUpload(files); // 复用之前的上传逻辑
//         }
//     });
// }
//
// /**
//  * 问答核心逻辑
//  */
// async function handleSendMessage() {
//     const input = document.getElementById('questionInput');
//     const question = input.value.trim();
//     if (!question) return;
//
//     // 清空预览和输入
//     document.getElementById('filePreviewArea').innerHTML = '';
//     input.value = '';
//
//     // 1. UI 显示用户问题
//     appendMessageUI('user', question, true);
//
//     try {
//         // 2. 调用 /ask 接口
//         const res = await axios.post(`${BASE_URL}/qa/ask`, {
//             question: question,
//             sessionId: currentSessionId
//         });
//
//         if (res.data.code === 200 || res.data.message.includes("任务")) {
//             // 3. 5秒后开始轮询
//             startPolling(currentSessionId);
//         }
//     } catch (err) {
//         appendMessageUI('ai', "请求失败，请检查网络设置。", true);
//     }
// }
//
// function startPolling(sid) {
//     // 先创建一个 AI 占位消息
//     const aiMsgObj = appendMessageUI('ai', "正在思考...", true);
//
//     setTimeout(() => {
//         const timer = setInterval(async () => {
//             try {
//                 // 仅在当前会话仍然是该 sid 时才更新 UI
//                 const res = await axios.get(`${BASE_URL}/qa/status`, {
//                     params: { sessionId: sid }
//                 });
//
//                 const data = res.data;
//                 const answer = data.data.answer || "生成中...";
//
//                 // 更新消息对象内容 (更新 UI + 更新存储的 Session 数据)
//                 updateMessageContent(sid, aiMsgObj, answer);
//
//                 // 检查结束标志
//                 if (data.code === 200 || answer.includes("<最终回答>")) {
//                     clearInterval(timer);
//                     const finalAnswer = answer.replace("<最终回答>", "");
//                     updateMessageContent(sid, aiMsgObj, finalAnswer);
//                 }
//             } catch (err) {
//                 console.error("轮询异常", err);
//                 clearInterval(timer);
//             }
//         }, 2000);
//     }, 5000);
// }
//
// /**
//  * UI 辅助：向界面添加气泡并存入内存
//  */
// function appendMessageUI(role, text, shouldSave) {
//     const area = document.getElementById('message-area');
//     const isUser = role === 'user';
//     const row = document.createElement('div');
//     row.className = `message-row ${isUser ? 'user-row' : 'ai-row'}`;
//
//     row.innerHTML = `
//         ${!isUser ? '<div class="avatar"><i class="bi bi-robot"></i></div>' : ''}
//         <div class="message-content shadow-sm">
//             <div class="msg-text">${text}</div>
//         </div>
//     `;
//
//     area.appendChild(row);
//     area.scrollTop = area.scrollHeight;
//
//     const msgObj = { role, text, element: row.querySelector('.msg-text') };
//
//     if (shouldSave && currentSessionId !== null) {
//         const session = sessions.find(s => s.id === currentSessionId);
//         if (session) session.messages.push(msgObj);
//
//         // 如果是第一条消息，更新会话标题
//         if (isUser && session.messages.length === 1) {
//             session.title = text.substring(0, 15) + (text.length > 15 ? '...' : '');
//             renderSessionList();
//         }
//     }
//     return msgObj;
// }
//
// function updateMessageContent(sid, msgObj, newText) {
//     // 更新 DOM
//     if (currentSessionId === sid) {
//         msgObj.element.innerText = newText;
//         const area = document.getElementById('message-area');
//         area.scrollTop = area.scrollHeight;
//     }
//     // 更新内存数据
//     msgObj.text = newText;
// }


