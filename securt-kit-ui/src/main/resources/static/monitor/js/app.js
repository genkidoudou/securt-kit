/**
 * Securt-Kit 监控页面 JavaScript
 */

// 配置
const CONFIG = {
    basePath: '/monitor',
    apiPath: '/monitor/api'
};

// 工具函数
const utils = {
    /**
     * 显示错误消息
     */
    showError(elementId, message) {
        const element = document.getElementById(elementId);
        if (element) {
            element.textContent = message;
            element.classList.add('show');
        }
    },

    /**
     * 隐藏错误消息
     */
    hideError(elementId) {
        const element = document.getElementById(elementId);
        if (element) {
            element.textContent = '';
            element.classList.remove('show');
        }
    },

    /**
     * API 请求
     */
    async request(url, options = {}) {
        const defaultOptions = {
            headers: {
                'Content-Type': 'application/json'
            }
        };

        const response = await fetch(url, { ...defaultOptions, ...options });
        const data = await response.json();
        return data;
    },

    /**
     * 复制到剪贴板
     */
    async copyToClipboard(text) {
        try {
            await navigator.clipboard.writeText(text);
            return true;
        } catch (err) {
            // 降级方案
            const textarea = document.createElement('textarea');
            textarea.value = text;
            textarea.style.position = 'fixed';
            textarea.style.opacity = '0';
            document.body.appendChild(textarea);
            textarea.select();
            try {
                document.execCommand('copy');
                document.body.removeChild(textarea);
                return true;
            } catch (e) {
                document.body.removeChild(textarea);
                return false;
            }
        }
    },

    /**
     * 显示通知
     * 只对错误类型显示 alert，成功类型静默处理
     */
    showNotification(message, type = 'success') {
        if (type === 'error') {
            // 只对错误显示弹框
            alert(message);
        }
        // 成功时不显示弹框，静默处理
    }
};

// 登录相关
const login = {
    /**
     * 检查登录状态
     */
    async checkLoginStatus() {
        try {
            const response = await utils.request(`${CONFIG.apiPath}/check.json`);
            if (response.success && response.data.loggedIn) {
                this.showMainPage();
            } else {
                this.showLoginPage();
            }
        } catch (error) {
            console.error('检查登录状态失败:', error);
            this.showLoginPage();
        }
    },

    /**
     * 处理登录
     */
    async handleLogin(event) {
        event.preventDefault();
        utils.hideError('loginError');

        const username = document.getElementById('username').value;
        const password = document.getElementById('password').value;

        if (!username || !password) {
            utils.showError('loginError', '请输入用户名和密码');
            return;
        }

        try {
            const formData = new URLSearchParams();
            formData.append('username', username);
            formData.append('password', password);

            const response = await fetch(`${CONFIG.basePath}/login`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: formData
            });

            const data = await response.json();

            if (data.success) {
                this.showMainPage();
            } else {
                utils.showError('loginError', data.message || '登录失败');
            }
        } catch (error) {
            console.error('登录失败:', error);
            utils.showError('loginError', '登录失败，请稍后重试');
        }
    },

    /**
     * 处理退出登录
     */
    async handleLogout() {
        try {
            await fetch(`${CONFIG.basePath}/logout`, { method: 'GET' });
            this.showLoginPage();
        } catch (error) {
            console.error('退出登录失败:', error);
        }
    },

    /**
     * 显示登录页面
     */
    showLoginPage() {
        document.getElementById('loginPage').style.display = 'block';
        document.getElementById('mainPage').style.display = 'none';
    },

    /**
     * 显示主页面
     */
    showMainPage() {
        document.getElementById('loginPage').style.display = 'none';
        document.getElementById('mainPage').style.display = 'block';
    }
};

// 加密解密功能（合并）
const crypto = {
    /**
     * 获取输入元素
     */
    getInputElement(id) {
        const element = document.getElementById(id);
        if (!element) {
            console.error(`Element not found: ${id}`);
            throw new Error(`Element not found: ${id}`);
        }
        return element;
    },

    /**
     * 处理加密
     */
    async handleEncrypt() {
        try {
            const text = this.getInputElement('cryptoInput').value.trim();
            const tableName = this.getInputElement('cryptoTableName').value.trim();
            const fieldName = this.getInputElement('cryptoFieldName').value.trim();
            const strategy = this.getInputElement('cryptoStrategy').value.trim();

            if (!text) {
                utils.showNotification('请输入要加密的文本', 'error');
                return;
            }

            const requestBody = { text };
            if (tableName) requestBody.tableName = tableName;
            if (fieldName) requestBody.fieldName = fieldName;
            if (strategy) requestBody.strategy = strategy;

            const response = await utils.request(`${CONFIG.apiPath}/encrypt.json`, {
                method: 'POST',
                body: JSON.stringify(requestBody)
            });

            if (response.success) {
                this.getInputElement('cryptoOutput').value = response.data.encrypted;
                this.getInputElement('copyBtn').style.display = 'inline-block';
                // 成功时不显示弹框，静默处理
            } else {
                utils.showNotification(response.message || '加密失败', 'error');
            }
        } catch (error) {
            console.error('加密失败:', error);
            utils.showNotification('加密失败: ' + (error.message || '请稍后重试'), 'error');
        }
    },

    /**
     * 处理解密
     */
    async handleDecrypt() {
        try {
            const text = this.getInputElement('cryptoInput').value.trim();
            const tableName = this.getInputElement('cryptoTableName').value.trim();
            const fieldName = this.getInputElement('cryptoFieldName').value.trim();
            const strategy = this.getInputElement('cryptoStrategy').value.trim();

            if (!text) {
                utils.showNotification('请输入要解密的文本', 'error');
                return;
            }

            const requestBody = { text };
            if (tableName) requestBody.tableName = tableName;
            if (fieldName) requestBody.fieldName = fieldName;
            if (strategy) requestBody.strategy = strategy;

            const response = await utils.request(`${CONFIG.apiPath}/decrypt.json`, {
                method: 'POST',
                body: JSON.stringify(requestBody)
            });

            if (response.success) {
                this.getInputElement('cryptoOutput').value = response.data.decrypted;
                this.getInputElement('copyBtn').style.display = 'inline-block';
                // 成功时不显示弹框，静默处理
            } else {
                utils.showNotification(response.message || '解密失败', 'error');
            }
        } catch (error) {
            console.error('解密失败:', error);
            utils.showNotification('解密失败: ' + (error.message || '请稍后重试'), 'error');
        }
    },

    /**
     * 清空输入和输出
     */
    clear() {
        try {
            this.getInputElement('cryptoInput').value = '';
            this.getInputElement('cryptoTableName').value = '';
            this.getInputElement('cryptoFieldName').value = '';
            this.getInputElement('cryptoStrategy').value = '';
            this.getInputElement('cryptoOutput').value = '';
            this.getInputElement('copyBtn').style.display = 'none';
        } catch (error) {
            console.error('清空失败:', error);
        }
    },

    /**
     * 复制结果
     */
    async copyResult() {
        try {
            const text = this.getInputElement('cryptoOutput').value;
            if (text) {
                const success = await utils.copyToClipboard(text);
                // 复制成功时静默处理，不显示弹框
                if (!success) {
                    utils.showNotification('复制失败', 'error');
                }
            }
        } catch (error) {
            console.error('复制失败:', error);
            utils.showNotification('复制失败', 'error');
        }
    }
};

// 标签页功能
const tabs = {
    /**
     * 切换标签页
     */
    switchTab(tabName) {
        // 移除所有活动状态
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.classList.remove('active');
        });
        document.querySelectorAll('.tab-content').forEach(content => {
            content.classList.remove('active');
        });

        // 激活选中的标签页
        const activeBtn = document.querySelector(`[data-tab="${tabName}"]`);
        const activeContent = document.getElementById(`${tabName}Tab`);

        if (activeBtn) {
            activeBtn.classList.add('active');
        }
        if (activeContent) {
            activeContent.classList.add('active');
        }
    },

    /**
     * 初始化标签页
     */
    init() {
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const tabName = btn.getAttribute('data-tab');
                this.switchTab(tabName);
            });
        });
    }
};

// SQL 解析功能
const sqlParser = {
    /**
     * 处理 SQL 解析
     */
    async handleParseSql() {
        try {
            const sqlInput = document.getElementById('sqlInput');
            if (!sqlInput) {
                throw new Error('SQL 输入框未找到');
            }

            const sql = sqlInput.value.trim();
            if (!sql) {
                utils.showNotification('请输入要解析的 SQL 语句', 'error');
                return;
            }

            const response = await utils.request(`${CONFIG.apiPath}/parse-sql.json`, {
                method: 'POST',
                body: JSON.stringify({ sql })
            });

            if (response.success) {
                this.displayResult(response.data);
            } else {
                utils.showNotification(response.message || 'SQL 解析失败', 'error');
            }
        } catch (error) {
            console.error('SQL 解析失败:', error);
            utils.showNotification('SQL 解析失败: ' + (error.message || '请稍后重试'), 'error');
        }
    },

    /**
     * 显示解析结果
     */
    displayResult(data) {
        const resultDiv = document.getElementById('parseSqlResult');
        if (!resultDiv) return;

        resultDiv.style.display = 'block';

        // 显示占位符映射
        this.renderPlaceholderTable(data.placeholderMap || {});

        // 显示加密字段列表
        this.renderEncryptFieldsTable(data.encryptFields || []);
    },

    /**
     * 渲染占位符映射表格
     */
    renderPlaceholderTable(placeholderMap) {
        const container = document.getElementById('placeholderTable');
        if (!container) return;

        const entries = Object.entries(placeholderMap);
        if (entries.length === 0) {
            container.innerHTML = '<div class="empty-message">无占位符映射</div>';
            return;
        }

        // 按索引排序
        entries.sort((a, b) => {
            const indexA = a[1].index || 0;
            const indexB = b[1].index || 0;
            return indexA - indexB;
        });

        let html = '<table><thead><tr>';
        html += '<th>占位符索引</th>';
        html += '<th>占位符名称</th>';
        html += '<th>表别名</th>';
        html += '<th>真实表名</th>';
        html += '<th>真实字段名</th>';
        html += '<th>是否来自真实表</th>';
        html += '<th>INSERT字段索引</th>';
        html += '<th>参数属性</th>';
        html += '<th>参数类型</th>';
        html += '</tr></thead><tbody>';

        for (const [key, info] of entries) {
            html += '<tr>';
            html += `<td>${info.index !== null && info.index !== undefined ? info.index : '-'}</td>`;
            html += `<td>${this.escapeHtml(key)}</td>`;
            html += `<td>${this.escapeHtml(info.tableAliasName || '-')}</td>`;
            html += `<td>${this.escapeHtml(info.sourceTableName || '-')}</td>`;
            html += `<td>${this.escapeHtml(info.sourceColumn || '-')}</td>`;
            html += `<td>${info.fromSourceTable ? '是' : '否'}</td>`;
            html += `<td>${info.insertFieldIndex !== null && info.insertFieldIndex !== undefined ? info.insertFieldIndex : '-'}</td>`;
            html += `<td>${this.escapeHtml(info.parameterProperty || '-')}</td>`;
            html += `<td>${this.escapeHtml(info.parameterType || '-')}</td>`;
            html += '</tr>';
        }

        html += '</tbody></table>';
        container.innerHTML = html;
    },

    /**
     * 渲染加密字段表格
     */
    renderEncryptFieldsTable(encryptFields) {
        const container = document.getElementById('encryptFieldsTable');
        if (!container) return;

        if (encryptFields.length === 0) {
            container.innerHTML = '<div class="empty-message">无需要加密的字段</div>';
            return;
        }

        let html = '<table><thead><tr>';
        html += '<th>列名/别名</th>';
        html += '<th>源表名</th>';
        html += '<th>源字段名</th>';
        html += '<th>加密策略</th>';
        html += '</tr></thead><tbody>';

        for (const field of encryptFields) {
            html += '<tr>';
            html += `<td>${this.escapeHtml(field.columnName || '-')}</td>`;
            html += `<td>${this.escapeHtml(field.sourceTableName || '-')}</td>`;
            html += `<td>${this.escapeHtml(field.sourceColumn || '-')}</td>`;
            html += `<td>${this.escapeHtml(field.fieldEncryptor || '-')}</td>`;
            html += '</tr>';
        }

        html += '</tbody></table>';
        container.innerHTML = html;
    },

    /**
     * HTML 转义
     */
    escapeHtml(text) {
        if (text == null) return '-';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    },

    /**
     * 清空 SQL 输入和结果
     */
    clear() {
        const sqlInput = document.getElementById('sqlInput');
        const resultDiv = document.getElementById('parseSqlResult');
        
        if (sqlInput) {
            sqlInput.value = '';
        }
        if (resultDiv) {
            resultDiv.style.display = 'none';
        }
    }
};

// 初始化
document.addEventListener('DOMContentLoaded', () => {
    // 绑定登录事件
    const loginForm = document.getElementById('loginForm');
    const logoutBtn = document.getElementById('logoutBtn');
    
    if (loginForm) {
        loginForm.addEventListener('submit', (e) => login.handleLogin(e));
    }
    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => login.handleLogout());
    }

    // 绑定加密解密事件（延迟绑定，确保元素已加载）
    const encryptBtn = document.getElementById('encryptBtn');
    const decryptBtn = document.getElementById('decryptBtn');
    const clearBtn = document.getElementById('clearBtn');
    const copyBtn = document.getElementById('copyBtn');
    const cryptoInput = document.getElementById('cryptoInput');

    if (encryptBtn) {
        encryptBtn.addEventListener('click', () => crypto.handleEncrypt());
    }
    if (decryptBtn) {
        decryptBtn.addEventListener('click', () => crypto.handleDecrypt());
    }
    if (clearBtn) {
        clearBtn.addEventListener('click', () => crypto.clear());
    }
    if (copyBtn) {
        copyBtn.addEventListener('click', () => crypto.copyResult());
    }
    if (cryptoInput) {
        // 支持 Ctrl+Enter 快捷键
        cryptoInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && e.ctrlKey) {
                crypto.handleEncrypt();
            }
        });
    }

    // 绑定 SQL 解析事件
    const parseSqlBtn = document.getElementById('parseSqlBtn');
    const clearSqlBtn = document.getElementById('clearSqlBtn');
    const sqlInput = document.getElementById('sqlInput');

    if (parseSqlBtn) {
        parseSqlBtn.addEventListener('click', () => sqlParser.handleParseSql());
    }
    if (clearSqlBtn) {
        clearSqlBtn.addEventListener('click', () => sqlParser.clear());
    }
    if (sqlInput) {
        // 支持 Ctrl+Enter 快捷键
        sqlInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && e.ctrlKey) {
                sqlParser.handleParseSql();
            }
        });
    }

    // 初始化标签页
    tabs.init();

    // 检查登录状态
    login.checkLoginStatus();
});

