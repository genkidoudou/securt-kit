/**
 * Securt-Kit 监控页面 JavaScript
 */

// 配置
const CONFIG = {
    basePath: '/monitor',
    apiPath: '/monitor/api'
};

// 数据源管理
const datasourceManager = {
    /**
     * 数据源列表
     */
    datasourceIds: [],
    defaultDatasourceId: 'default',

    /**
     * 获取数据源列表
     */
    async fetchDatasources() {
        try {
            console.log('开始获取数据源列表...');
            const response = await utils.request(`${CONFIG.apiPath}/datasources.json`);
            console.log('数据源列表响应:', response);
            
            if (response.success && response.data) {
                this.datasourceIds = response.data.datasourceIds || [];
                this.defaultDatasourceId = response.data.defaultDatasourceId || 'default';
                console.log('数据源列表:', this.datasourceIds);
                console.log('默认数据源ID:', this.defaultDatasourceId);
                this.populateSelects();
            } else {
                console.warn('获取数据源列表失败，响应:', response);
                this.datasourceIds = [];
                this.defaultDatasourceId = 'default';
            }
        } catch (error) {
            console.error('获取数据源列表失败:', error);
            // 失败时使用默认值
            this.datasourceIds = [];
            this.defaultDatasourceId = 'default';
        }
    },

    /**
     * 填充所有数据源单选框按钮组
     * 
     * <p>说明：</p>
     * <ul>
     *   <li>显示所有数据源（包括 "default"）</li>
     *   <li>使用单选框按钮样式，必须选择一个数据源</li>
     *   <li>默认选中第一个数据源</li>
     * </ul>
     */
    populateSelects() {
        const groupIds = [
            'cryptoDatasourceId',
            'sqlParseDatasourceId',
            'sqlEncryptDatasourceId',
            'sqlQueryDatasourceId',
            'dataInitDatasourceId'
        ];

        // 如果没有数据源，显示提示
        if (!this.datasourceIds || this.datasourceIds.length === 0) {
            groupIds.forEach(groupId => {
                const group = document.getElementById(groupId);
                if (group) {
                    group.innerHTML = '<div style="color: #999; padding: 8px;">暂无可用数据源</div>';
                }
            });
            return;
        }

        groupIds.forEach((groupId, groupIndex) => {
            const group = document.getElementById(groupId);
            if (!group) {
                console.warn(`数据源组元素未找到: ${groupId}`);
                return;
            }
            
            group.innerHTML = '';
            console.log(`填充数据源组: ${groupId}, 数据源数量: ${this.datasourceIds.length}`);
            
            // 添加所有数据源选项
            this.datasourceIds.forEach((dsId, index) => {
                const radioItem = document.createElement('div');
                radioItem.className = 'datasource-radio-item';
                
                const radio = document.createElement('input');
                radio.type = 'radio';
                radio.name = groupId; // 同一组内的单选框使用相同的 name
                radio.id = `${groupId}_${dsId}`;
                radio.value = dsId;
                // 每个组的第一个选项默认选中
                if (index === 0) {
                    radio.checked = true;
                }
                
                const label = document.createElement('label');
                label.setAttribute('for', `${groupId}_${dsId}`);
                label.textContent = dsId;
                
                // 将 label 和 radio 都添加到 radioItem 中
                radioItem.appendChild(radio);
                radioItem.appendChild(label);
                
                // 添加点击事件到 label，确保可以点击
                // 使用箭头函数保持 this 上下文
                const handleClick = (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    console.log(`点击数据源按钮: ${dsId} (组: ${groupId})`);
                    
                    // 取消同组其他单选框的选中状态
                    const allRadios = group.querySelectorAll('input[type="radio"]');
                    allRadios.forEach(r => {
                        if (r !== radio) {
                            r.checked = false;
                        }
                    });
                    
                    // 选中当前单选框
                    radio.checked = true;
                    
                    // 触发 change 事件
                    const changeEvent = new Event('change', { bubbles: true });
                    radio.dispatchEvent(changeEvent);
                    
                    console.log(`已选中数据源: ${dsId} (组: ${groupId}), checked: ${radio.checked}`);
                };
                
                label.addEventListener('click', handleClick);
                
                // 也添加点击事件到整个 radioItem 作为备用
                radioItem.addEventListener('click', function(e) {
                    // 如果点击的不是 label 或 radio，触发 label 的点击
                    if (e.target === radioItem || (e.target !== label && e.target !== radio)) {
                        handleClick(e);
                    }
                });
                
                group.appendChild(radioItem);
            });
            
            console.log(`数据源组 ${groupId} 填充完成，共 ${this.datasourceIds.length} 个选项`);
        });
    },

    /**
     * 获取选中的数据源ID
     * 
     * <p>说明：</p>
     * <ul>
     *   <li>从单选框按钮组中获取选中的数据源ID</li>
     *   <li>必须选择一个数据源，如果没有选中则返回 null</li>
     * </ul>
     * 
     * @param {string} groupId 单选框按钮组ID
     * @returns {string|null} 数据源ID，如果没有选中则返回 null
     */
    getSelectedDatasourceId(groupId) {
        const group = document.getElementById(groupId);
        if (!group) {
            console.warn(`数据源组未找到: ${groupId}`);
            return null;
        }
        
        // 查找选中的单选框
        const checkedRadio = group.querySelector('input[type="radio"]:checked');
        if (checkedRadio) {
            const value = checkedRadio.value;
            console.log(`获取选中的数据源: ${value} (组: ${groupId})`);
            return value;
        }
        
        console.warn(`未找到选中的数据源 (组: ${groupId})`);
        // 如果没有选中的，尝试选择第一个
        const firstRadio = group.querySelector('input[type="radio"]');
        if (firstRadio) {
            firstRadio.checked = true;
            console.log(`自动选中第一个数据源: ${firstRadio.value} (组: ${groupId})`);
            return firstRadio.value;
        }
        
        return null;
    },
    
    /**
     * 验证数据源是否已选择
     * 
     * @param {string} groupId 单选框按钮组ID
     * @param {string} errorElementId 错误提示元素ID
     * @returns {boolean} 如果已选择返回 true，否则返回 false
     */
    validateDatasource(groupId, errorElementId) {
        const datasourceId = this.getSelectedDatasourceId(groupId);
        const errorElement = document.getElementById(errorElementId);
        
        if (!datasourceId) {
            if (errorElement) {
                errorElement.textContent = '请选择数据源';
                errorElement.style.display = 'block';
            }
            return false;
        }
        
        if (errorElement) {
            errorElement.style.display = 'none';
        }
        return true;
    }
};

// 工具函数
const utils = {
    /**
     * 转义 HTML 特殊字符，防止 XSS 攻击
     */
    escapeHtml(text) {
        if (text == null) {
            return '';
        }
        const map = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        };
        return String(text).replace(/[&<>"']/g, m => map[m]);
    },

    /**
     * 安全地设置文本内容（自动转义 HTML）
     */
    setTextContent(element, text) {
        if (element) {
            element.textContent = text; // textContent 自动转义，比 innerHTML 安全
        }
    },

    /**
     * 安全地设置 HTML 内容（需要先转义）
     */
    setHtmlContent(element, html) {
        if (element) {
            element.innerHTML = this.escapeHtml(html);
        }
    },

    /**
     * 显示错误消息（安全输出）
     */
    showError(elementId, message) {
        const element = document.getElementById(elementId);
        if (element) {
            this.setTextContent(element, message);
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
        // 显示主页面后加载数据源列表
        datasourceManager.fetchDatasources();
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

            // 验证数据源是否已选择
            if (!datasourceManager.validateDatasource('cryptoDatasourceId', 'cryptoDatasourceError')) {
                return;
            }

            const requestBody = { text };
            if (tableName) requestBody.tableName = tableName;
            if (fieldName) requestBody.fieldName = fieldName;
            if (strategy) requestBody.strategy = strategy;
            
            // 添加数据源ID（必选）
            const datasourceId = datasourceManager.getSelectedDatasourceId('cryptoDatasourceId');
            requestBody.datasourceId = datasourceId;

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

            // 验证数据源是否已选择
            if (!datasourceManager.validateDatasource('cryptoDatasourceId', 'cryptoDatasourceError')) {
                return;
            }

            const requestBody = { text };
            if (tableName) requestBody.tableName = tableName;
            if (fieldName) requestBody.fieldName = fieldName;
            if (strategy) requestBody.strategy = strategy;
            
            // 添加数据源ID（必选）
            const datasourceId = datasourceManager.getSelectedDatasourceId('cryptoDatasourceId');
            requestBody.datasourceId = datasourceId;

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
        console.log('switchTab 被调用，tabName:', tabName);
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

        // 如果切换到配置信息标签页，自动加载配置
        if (tabName === 'config') {
            console.log('检测到切换到配置信息标签页，准备加载配置信息');
            console.log('configInfo 对象:', configInfo);
            console.log('configInfo.loadConfig 类型:', typeof configInfo?.loadConfig);
            
            // 确保 configInfo 已定义
            if (typeof configInfo === 'undefined') {
                console.error('configInfo 未定义！');
                return;
            }
            
            if (configInfo && typeof configInfo.loadConfig === 'function') {
                console.log('立即调用 loadConfig 方法');
                // 使用 setTimeout 确保 DOM 已更新
                setTimeout(() => {
                    configInfo.loadConfig();
                }, 50);
            } else {
                console.error('configInfo.loadConfig 不是函数', {
                    configInfo: configInfo,
                    loadConfigType: typeof configInfo?.loadConfig
                });
            }
        }
    },

    /**
     * 初始化标签页
     */
    init() {
        console.log('初始化标签页功能');
        const tabButtons = document.querySelectorAll('.tab-btn');
        console.log('找到标签页按钮数量:', tabButtons.length);
        tabButtons.forEach((btn, index) => {
            const tabName = btn.getAttribute('data-tab');
            console.log(`标签页 ${index}: data-tab="${tabName}"`);
            btn.addEventListener('click', () => {
                console.log('标签页按钮被点击，data-tab:', tabName);
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

            // 验证数据源是否已选择
            if (!datasourceManager.validateDatasource('sqlParseDatasourceId', 'sqlParseDatasourceError')) {
                return;
            }

            const requestBody = { sql };
            // 添加数据源ID（必选）
            const datasourceId = datasourceManager.getSelectedDatasourceId('sqlParseDatasourceId');
            requestBody.datasourceId = datasourceId;

            const response = await utils.request(`${CONFIG.apiPath}/parse-sql.json`, {
                method: 'POST',
                body: JSON.stringify(requestBody)
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
            utils.setHtmlContent(container, '<div class="empty-message">无占位符映射</div>');
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
            html += `<td>${utils.escapeHtml(key)}</td>`;
            html += `<td>${utils.escapeHtml(info.tableAliasName || '-')}</td>`;
            html += `<td>${utils.escapeHtml(info.sourceTableName || '-')}</td>`;
            html += `<td>${utils.escapeHtml(info.sourceColumn || '-')}</td>`;
            html += `<td>${info.fromSourceTable ? '是' : '否'}</td>`;
            html += `<td>${info.insertFieldIndex !== null && info.insertFieldIndex !== undefined ? info.insertFieldIndex : '-'}</td>`;
            html += `<td>${utils.escapeHtml(info.parameterProperty || '-')}</td>`;
            html += `<td>${utils.escapeHtml(info.parameterType || '-')}</td>`;
            html += '</tr>';
        }

        html += '</tbody></table>';
        container.innerHTML = html; // HTML 结构已转义，安全
    },

    /**
     * 渲染加密字段表格
     */
    renderEncryptFieldsTable(encryptFields) {
        const container = document.getElementById('encryptFieldsTable');
        if (!container) return;

        if (encryptFields.length === 0) {
            utils.setHtmlContent(container, '<div class="empty-message">无需要加密的字段</div>');
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
            html += `<td>${utils.escapeHtml(field.columnName || '-')}</td>`;
            html += `<td>${utils.escapeHtml(field.sourceTableName || '-')}</td>`;
            html += `<td>${utils.escapeHtml(field.sourceColumn || '-')}</td>`;
            html += `<td>${utils.escapeHtml(field.fieldEncryptor || '-')}</td>`;
            html += '</tr>';
        }

        html += '</tbody></table>';
        container.innerHTML = html; // HTML 结构已转义，安全
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

// SQL 加密功能
const sqlEncrypt = {
    /**
     * 处理 SQL 加密
     */
    async handleEncryptSql() {
        try {
            const sqlInput = document.getElementById('sqlEncryptInput');
            if (!sqlInput) {
                throw new Error('SQL 输入框未找到');
            }

            const sql = sqlInput.value.trim();
            if (!sql) {
                utils.showNotification('请输入要加密的 SQL 语句', 'error');
                return;
            }

            // 验证数据源是否已选择
            if (!datasourceManager.validateDatasource('sqlEncryptDatasourceId', 'sqlEncryptDatasourceError')) {
                return;
            }

            const requestBody = { sql };
            // 添加数据源ID（必选）
            const datasourceId = datasourceManager.getSelectedDatasourceId('sqlEncryptDatasourceId');
            requestBody.datasourceId = datasourceId;

            const response = await utils.request(`${CONFIG.apiPath}/encrypt-sql.json`, {
                method: 'POST',
                body: JSON.stringify(requestBody)
            });

            if (response.success) {
                this.displayResult(response.data);
            } else {
                utils.showNotification(response.message || 'SQL 加密失败', 'error');
            }
        } catch (error) {
            console.error('SQL 加密失败:', error);
            utils.showNotification('SQL 加密失败: ' + (error.message || '请稍后重试'), 'error');
        }
    },

    /**
     * 显示加密结果
     */
    displayResult(data) {
        const resultDiv = document.getElementById('sqlEncryptResult');
        const outputTextarea = document.getElementById('sqlEncryptOutput');
        const originalTextarea = document.getElementById('sqlEncryptOriginal');
        const statsDiv = document.getElementById('sqlEncryptStats');
        const copyBtn = document.getElementById('copySqlEncryptBtn');

        if (!resultDiv || !outputTextarea || !originalTextarea || !statsDiv) return;

        resultDiv.style.display = 'block';

        // 显示原始 SQL
        originalTextarea.value = data.originalSql || '';

        // 显示加密后的 SQL
        outputTextarea.value = data.encryptedSql || '';

        // 显示统计信息
        const encryptedCount = data.encryptedFieldCount || 0;
        if (encryptedCount > 0) {
            statsDiv.textContent = `已加密 ${encryptedCount} 个字段的值`;
            statsDiv.style.color = '#1890ff';
        } else {
            statsDiv.textContent = '未发现需要加密的字段';
            statsDiv.style.color = '#999';
        }

        // 显示复制按钮
        if (copyBtn) {
            copyBtn.style.display = 'inline-block';
        }
    },

    /**
     * 清空 SQL 输入和结果
     */
    clear() {
        const sqlInput = document.getElementById('sqlEncryptInput');
        const resultDiv = document.getElementById('sqlEncryptResult');
        const outputTextarea = document.getElementById('sqlEncryptOutput');
        const originalTextarea = document.getElementById('sqlEncryptOriginal');
        const statsDiv = document.getElementById('sqlEncryptStats');
        const copyBtn = document.getElementById('copySqlEncryptBtn');

        if (sqlInput) {
            sqlInput.value = '';
        }
        if (resultDiv) {
            resultDiv.style.display = 'none';
        }
        if (outputTextarea) {
            outputTextarea.value = '';
        }
        if (originalTextarea) {
            originalTextarea.value = '';
        }
        if (statsDiv) {
            statsDiv.textContent = '';
        }
        if (copyBtn) {
            copyBtn.style.display = 'none';
        }
    },

    /**
     * 复制加密后的 SQL
     */
    async copyResult() {
        try {
            const outputTextarea = document.getElementById('sqlEncryptOutput');
            if (!outputTextarea) return;

            const text = outputTextarea.value;
            if (text) {
                const success = await utils.copyToClipboard(text);
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

// SQL 查询功能
const sqlQuery = {
    /**
     * 处理 SQL 查询
     */
    async handleQuery() {
        try {
            const sqlInput = document.getElementById('sqlQueryInput');
            const pageSizeInput = document.getElementById('sqlQueryPageSize');
            const pageNumInput = document.getElementById('sqlQueryPageNum');

            if (!sqlInput) {
                throw new Error('SQL 输入框未找到');
            }

            const sql = sqlInput.value.trim();
            if (!sql) {
                utils.showNotification('请输入要查询的 SQL 语句', 'error');
                return;
            }

            // 验证是否为 SELECT 语句
            if (!sql.toUpperCase().startsWith('SELECT')) {
                utils.showNotification('只支持 SELECT 查询语句', 'error');
                return;
            }

            // 验证数据源是否已选择
            if (!datasourceManager.validateDatasource('sqlQueryDatasourceId', 'sqlQueryDatasourceError')) {
                return;
            }

            const pageSize = pageSizeInput ? parseInt(pageSizeInput.value) || 10 : 10;
            const pageNum = pageNumInput ? parseInt(pageNumInput.value) || 1 : 1;

            const requestBody = {
                sql: sql,
                pageSize: pageSize,
                pageNum: pageNum
            };
            // 添加数据源ID（必选）
            const datasourceId = datasourceManager.getSelectedDatasourceId('sqlQueryDatasourceId');
            requestBody.datasourceId = datasourceId;

            const response = await utils.request(`${CONFIG.apiPath}/query-sql.json`, {
                method: 'POST',
                body: JSON.stringify(requestBody)
            });

            if (response.success) {
                this.displayResult(response.data);
            } else {
                utils.showNotification(response.message || 'SQL 查询失败', 'error');
            }
        } catch (error) {
            console.error('SQL 查询失败:', error);
            utils.showNotification('SQL 查询失败: ' + (error.message || '请稍后重试'), 'error');
        }
    },

    /**
     * 显示查询结果
     */
    displayResult(data) {
        const resultDiv = document.getElementById('sqlQueryResult');
        const executedTextarea = document.getElementById('sqlQueryExecuted');
        const statsDiv = document.getElementById('sqlQueryStats');
        const tableContainer = document.getElementById('sqlQueryTable');

        if (!resultDiv || !executedTextarea || !statsDiv || !tableContainer) return;

        resultDiv.style.display = 'block';

        // 显示执行的 SQL
        executedTextarea.value = data.executedSql || '';

        // 显示统计信息
        const count = data.data ? data.data.length : 0;
        const totalCount = data.totalCount || count;
        statsDiv.textContent = `共查询到 ${count} 条记录（第 ${data.pageNum || 1} 页，每页 ${data.pageSize || 10} 条）`;

        // 渲染表格
        this.renderTable(data.columns || [], data.data || [], tableContainer);
    },

    /**
     * 渲染查询结果表格
     */
    renderTable(columns, data, container) {
        if (columns.length === 0 || data.length === 0) {
            utils.setHtmlContent(container, '<div class="empty-message">无查询结果</div>');
            return;
        }

        let html = '<table><thead><tr>';
        
        // 表头
        for (const column of columns) {
            html += `<th>${utils.escapeHtml(column)}</th>`;
        }
        html += '</tr></thead><tbody>';

        // 数据行
        for (const row of data) {
            html += '<tr>';
            for (const column of columns) {
                const value = row[column];
                html += `<td>${this.formatCellValue(value)}</td>`;
            }
            html += '</tr>';
        }

        html += '</tbody></table>';
        container.innerHTML = html; // HTML 结构已转义，安全
    },

    /**
     * 格式化单元格值
     */
    formatCellValue(value) {
        if (value == null) {
            return '<span style="color: #999;">NULL</span>';
        }
        if (typeof value === 'boolean') {
            return value ? 'true' : 'false';
        }
        if (typeof value === 'number') {
            return String(value);
        }
        // 字符串值进行 HTML 转义
        return utils.escapeHtml(String(value));
    },

    /**
     * 清空查询输入和结果
     */
    clear() {
        const sqlInput = document.getElementById('sqlQueryInput');
        const pageSizeInput = document.getElementById('sqlQueryPageSize');
        const pageNumInput = document.getElementById('sqlQueryPageNum');
        const resultDiv = document.getElementById('sqlQueryResult');
        const executedTextarea = document.getElementById('sqlQueryExecuted');
        const statsDiv = document.getElementById('sqlQueryStats');
        const tableContainer = document.getElementById('sqlQueryTable');

        if (sqlInput) {
            sqlInput.value = '';
        }
        if (pageSizeInput) {
            pageSizeInput.value = '10';
        }
        if (pageNumInput) {
            pageNumInput.value = '1';
        }
        if (resultDiv) {
            resultDiv.style.display = 'none';
        }
        if (executedTextarea) {
            executedTextarea.value = '';
        }
        if (statsDiv) {
            statsDiv.textContent = '';
        }
        if (tableContainer) {
            tableContainer.textContent = ''; // 使用 textContent 清空更安全
        }
    }
};

// 数据初始化功能
const dataInit = {
    /**
     * 处理数据初始化加密
     */
    async handleEncrypt() {
        try {
            const datasourceId = datasourceManager.getSelectedDatasourceId('dataInitDatasourceId');
            const tableName = document.getElementById('dataInitTableName').value.trim();
            const whereCondition = document.getElementById('dataInitWhereCondition').value.trim();
            const primaryKeyField = document.getElementById('dataInitPrimaryKeyField').value.trim();

            // 验证必填字段
            if (!datasourceId) {
                const errorDiv = document.getElementById('dataInitDatasourceError');
                if (errorDiv) {
                    errorDiv.textContent = '请选择数据源';
                    errorDiv.style.display = 'block';
                }
                utils.showNotification('请选择数据源', 'error');
                return;
            }
            
            // 清除错误提示
            const errorDiv = document.getElementById('dataInitDatasourceError');
            if (errorDiv) {
                errorDiv.style.display = 'none';
            }
            
            if (!tableName) {
                utils.showNotification('表名不能为空', 'error');
                return;
            }
            if (!primaryKeyField) {
                utils.showNotification('主键字段不能为空', 'error');
                return;
            }

            // 显示加载状态
            const encryptBtn = document.getElementById('dataInitEncryptBtn');
            const decryptBtn = document.getElementById('dataInitDecryptBtn');
            if (encryptBtn) {
                encryptBtn.disabled = true;
                encryptBtn.textContent = '加密中...';
            }
            if (decryptBtn) {
                decryptBtn.disabled = true;
            }

            try {
                const requestBody = {
                    datasourceId: datasourceId,
                    tableName: tableName,
                    primaryKeyField: primaryKeyField
                };
                if (whereCondition) {
                    requestBody.whereCondition = whereCondition;
                }

                const response = await utils.request(`${CONFIG.apiPath}/data-init/encrypt.json`, {
                    method: 'POST',
                    body: JSON.stringify(requestBody)
                });

                if (response.success) {
                    this.displayResult(response.data, 'encrypt');
                } else {
                    utils.showNotification(response.message || '加密失败', 'error');
                }
            } finally {
                // 恢复按钮状态
                if (encryptBtn) {
                    encryptBtn.disabled = false;
                    encryptBtn.textContent = '加密';
                }
                if (decryptBtn) {
                    decryptBtn.disabled = false;
                }
            }
        } catch (error) {
            console.error('数据初始化加密失败:', error);
            utils.showNotification('数据初始化加密失败: ' + (error.message || '请稍后重试'), 'error');
            
            // 恢复按钮状态
            const encryptBtn = document.getElementById('dataInitEncryptBtn');
            const decryptBtn = document.getElementById('dataInitDecryptBtn');
            if (encryptBtn) {
                encryptBtn.disabled = false;
                encryptBtn.textContent = '加密';
            }
            if (decryptBtn) {
                decryptBtn.disabled = false;
            }
        }
    },

    /**
     * 处理数据初始化解密
     */
    async handleDecrypt() {
        try {
            const datasourceId = datasourceManager.getSelectedDatasourceId('dataInitDatasourceId');
            const tableName = document.getElementById('dataInitTableName').value.trim();
            const whereCondition = document.getElementById('dataInitWhereCondition').value.trim();
            const primaryKeyField = document.getElementById('dataInitPrimaryKeyField').value.trim();

            // 验证必填字段
            if (!datasourceId) {
                const errorDiv = document.getElementById('dataInitDatasourceError');
                if (errorDiv) {
                    errorDiv.textContent = '请选择数据源';
                    errorDiv.style.display = 'block';
                }
                utils.showNotification('请选择数据源', 'error');
                return;
            }
            
            // 清除错误提示
            const errorDiv = document.getElementById('dataInitDatasourceError');
            if (errorDiv) {
                errorDiv.style.display = 'none';
            }
            
            if (!tableName) {
                utils.showNotification('表名不能为空', 'error');
                return;
            }
            if (!primaryKeyField) {
                utils.showNotification('主键字段不能为空', 'error');
                return;
            }

            // 显示加载状态
            const encryptBtn = document.getElementById('dataInitEncryptBtn');
            const decryptBtn = document.getElementById('dataInitDecryptBtn');
            if (decryptBtn) {
                decryptBtn.disabled = true;
                decryptBtn.textContent = '解密中...';
            }
            if (encryptBtn) {
                encryptBtn.disabled = true;
            }

            try {
                const requestBody = {
                    datasourceId: datasourceId,
                    tableName: tableName,
                    primaryKeyField: primaryKeyField
                };
                if (whereCondition) {
                    requestBody.whereCondition = whereCondition;
                }

                const response = await utils.request(`${CONFIG.apiPath}/data-init/decrypt.json`, {
                    method: 'POST',
                    body: JSON.stringify(requestBody)
                });

                if (response.success) {
                    this.displayResult(response.data, 'decrypt');
                } else {
                    utils.showNotification(response.message || '解密失败', 'error');
                }
            } finally {
                // 恢复按钮状态
                if (decryptBtn) {
                    decryptBtn.disabled = false;
                    decryptBtn.textContent = '解密';
                }
                if (encryptBtn) {
                    encryptBtn.disabled = false;
                }
            }
        } catch (error) {
            console.error('数据初始化解密失败:', error);
            utils.showNotification('数据初始化解密失败: ' + (error.message || '请稍后重试'), 'error');
            
            // 恢复按钮状态
            const encryptBtn = document.getElementById('dataInitEncryptBtn');
            const decryptBtn = document.getElementById('dataInitDecryptBtn');
            if (decryptBtn) {
                decryptBtn.disabled = false;
                decryptBtn.textContent = '解密';
            }
            if (encryptBtn) {
                encryptBtn.disabled = false;
            }
        }
    },

    /**
     * 显示处理结果
     */
    displayResult(data, operationType) {
        const resultDiv = document.getElementById('dataInitResult');
        const statsDiv = document.getElementById('dataInitStats');
        const sqlOutput = document.getElementById('dataInitSqlOutput');
        const copyBtn = document.getElementById('copyDataInitSqlBtn');

        if (!resultDiv || !statsDiv || !sqlOutput) return;

        resultDiv.style.display = 'block';

        // 显示统计信息
        const processedCount = data.processedCount || 0;
        const processedFieldCount = data.processedFieldCount || 0;
        const operationText = operationType === 'encrypt' ? '加密' : '解密';
        
        statsDiv.innerHTML = `
            <div style="margin-bottom: 10px;">
                <strong>${operationText}完成</strong>
            </div>
            <div>
                处理记录数: <span style="color: #1890ff; font-weight: bold;">${processedCount}</span> | 
                处理字段数: <span style="color: #1890ff; font-weight: bold;">${processedFieldCount}</span>
            </div>
        `;
        statsDiv.style.color = '#333';

        // 显示生成的SQL语句
        const sqlStatements = data.sqlStatements || [];
        if (sqlStatements.length > 0) {
            sqlOutput.value = sqlStatements.join(';\n') + ';';
            if (copyBtn) {
                copyBtn.style.display = 'inline-block';
            }
        } else {
            sqlOutput.value = '未生成SQL语句';
            if (copyBtn) {
                copyBtn.style.display = 'none';
            }
        }
    },

    /**
     * 清空输入和结果
     */
    clear() {
        const datasourceGroup = document.getElementById('dataInitDatasourceId');
        const tableNameInput = document.getElementById('dataInitTableName');
        const whereConditionInput = document.getElementById('dataInitWhereCondition');
        const primaryKeyFieldInput = document.getElementById('dataInitPrimaryKeyField');
        const resultDiv = document.getElementById('dataInitResult');
        const statsDiv = document.getElementById('dataInitStats');
        const sqlOutput = document.getElementById('dataInitSqlOutput');
        const copyBtn = document.getElementById('copyDataInitSqlBtn');
        const errorDiv = document.getElementById('dataInitDatasourceError');

        // 重置数据源选择为第一个选项
        if (datasourceGroup) {
            const firstRadio = datasourceGroup.querySelector('input[type="radio"]');
            if (firstRadio) {
                firstRadio.checked = true;
            }
        }
        
        // 清除错误提示
        if (errorDiv) {
            errorDiv.style.display = 'none';
        }
        if (tableNameInput) {
            tableNameInput.value = '';
        }
        if (whereConditionInput) {
            whereConditionInput.value = '';
        }
        if (primaryKeyFieldInput) {
            primaryKeyFieldInput.value = '';
        }
        if (resultDiv) {
            resultDiv.style.display = 'none';
        }
        if (statsDiv) {
            statsDiv.textContent = '';
        }
        if (sqlOutput) {
            sqlOutput.value = '';
        }
        if (copyBtn) {
            copyBtn.style.display = 'none';
        }
    },

    /**
     * 复制SQL语句
     */
    async copySql() {
        try {
            const sqlOutput = document.getElementById('dataInitSqlOutput');
            if (!sqlOutput) return;

            const text = sqlOutput.value;
            if (text) {
                const success = await utils.copyToClipboard(text);
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

// 配置信息功能
const configInfo = {
    // 测试方法：可以在浏览器控制台直接调用 configInfo.test()
    test() {
        console.log('configInfo 测试:', {
            configInfo: this,
            hasLoadConfig: typeof this.loadConfig === 'function',
            CONFIG: CONFIG
        });
        if (typeof this.loadConfig === 'function') {
            console.log('调用 loadConfig');
            this.loadConfig();
        }
    },
    /**
     * 加载配置信息
     */
    async loadConfig() {
        console.log('loadConfig 方法被调用');
        try {
            const loadingDiv = document.getElementById('configLoading');
            const resultDiv = document.getElementById('configResult');
            
            console.log('DOM 元素检查:', {
                loadingDiv: loadingDiv !== null,
                resultDiv: resultDiv !== null
            });
            
            if (loadingDiv) {
                loadingDiv.style.display = 'block';
                loadingDiv.textContent = '正在加载配置信息...';
                console.log('显示加载提示');
            }
            if (resultDiv) {
                resultDiv.style.display = 'none';
            }

            try {
                const apiUrl = `${CONFIG.apiPath}/config.json`;
                console.log('开始加载配置信息，请求URL:', apiUrl);
                console.log('CONFIG.apiPath:', CONFIG.apiPath);
                const response = await utils.request(apiUrl);
                console.log('配置信息响应:', response);

                if (response.success) {
                    console.log('配置信息数据:', response.data);
                    this.displayConfig(response.data);
                } else {
                    if (loadingDiv) {
                        loadingDiv.textContent = '加载配置失败: ' + (response.message || '未知错误');
                        loadingDiv.style.color = '#ff4d4f';
                    }
                    utils.showNotification(response.message || '加载配置失败', 'error');
                }
            } catch (error) {
                console.error('加载配置失败:', error);
                if (loadingDiv) {
                    loadingDiv.textContent = '加载配置失败: ' + (error.message || '请稍后重试');
                    loadingDiv.style.color = '#ff4d4f';
                }
                utils.showNotification('加载配置失败: ' + (error.message || '请稍后重试'), 'error');
            }
        } catch (error) {
            console.error('加载配置失败:', error);
            utils.showNotification('加载配置失败: ' + (error.message || '请稍后重试'), 'error');
        }
    },

    /**
     * 显示配置信息
     */
    displayConfig(data) {
        const resultDiv = document.getElementById('configResult');
        const loadingDiv = document.getElementById('configLoading');
        const basicConfigDiv = document.getElementById('basicConfig');
        const cacheConfigDiv = document.getElementById('cacheConfig');
        const tablesConfigDiv = document.getElementById('tablesConfig');

        if (!resultDiv || !basicConfigDiv || !cacheConfigDiv || !tablesConfigDiv) {
            return;
        }

        // 隐藏加载提示，显示结果
        if (loadingDiv) {
            loadingDiv.style.display = 'none';
        }
        resultDiv.style.display = 'block';

        // 显示基本配置 - 紧凑形式
        let basicHtml = '<div class="compact-config-list">';
        const enableText = data.enable !== null && data.enable !== undefined ? (data.enable ? '是' : '否') : '未配置';
        const enableClass = data.enable === true ? 'config-value enabled' : data.enable === false ? 'config-value disabled' : 'config-value';
        basicHtml += `<span class="config-item"><span class="config-label">是否启用:</span><span class="${enableClass}">${enableText}</span></span>`;
        basicHtml += `<span class="config-item"><span class="config-label">失败处理策略:</span><span class="config-value">${utils.escapeHtml(data.failurePolicy || 'FALLBACK')}</span></span>`;
        basicHtml += '</div>';
        basicConfigDiv.innerHTML = basicHtml;

        // 显示SQL解析缓存配置 - 紧凑形式
        if (data.sqlParseCache) {
            let cacheHtml = '<div class="compact-config-list">';
            const cacheEnableText = data.sqlParseCache.enable !== null && data.sqlParseCache.enable !== undefined ? (data.sqlParseCache.enable ? '是' : '否') : '未配置';
            const cacheEnableClass = data.sqlParseCache.enable === true ? 'config-value enabled' : data.sqlParseCache.enable === false ? 'config-value disabled' : 'config-value';
            cacheHtml += `<span class="config-item"><span class="config-label">是否启用缓存:</span><span class="${cacheEnableClass}">${cacheEnableText}</span></span>`;
            cacheHtml += `<span class="config-item"><span class="config-label">缓存最大容量:</span><span class="config-value">${data.sqlParseCache.maxSize !== null && data.sqlParseCache.maxSize !== undefined ? data.sqlParseCache.maxSize : '未配置'}</span></span>`;
            cacheHtml += '</div>';
            cacheConfigDiv.innerHTML = cacheHtml;
        } else {
            cacheConfigDiv.innerHTML = '<div class="empty-message">未配置SQL解析缓存</div>';
        }

        // 显示表配置 - 按数据源分组显示
        if (data.tables && data.tables.length > 0) {
            // 按数据源分组
            const tablesByDatasource = {};
            for (let i = 0; i < data.tables.length; i++) {
                const table = data.tables[i];
                const datasourceId = table.datasourceId || 'default';
                if (!tablesByDatasource[datasourceId]) {
                    tablesByDatasource[datasourceId] = [];
                }
                tablesByDatasource[datasourceId].push(table);
            }
            
            // 按数据源分组显示
            let tablesHtml = '';
            const datasourceIds = Object.keys(tablesByDatasource).sort();
            
            for (let dsIndex = 0; dsIndex < datasourceIds.length; dsIndex++) {
                const datasourceId = datasourceIds[dsIndex];
                const tables = tablesByDatasource[datasourceId];
                
                // 数据源分组标题
                tablesHtml += `<div class="datasource-group">`;
                tablesHtml += `<div class="datasource-group-header">`;
                tablesHtml += `<span class="datasource-badge large">${utils.escapeHtml(datasourceId)}</span>`;
                tablesHtml += `<span class="datasource-group-count">(${tables.length} 个表)</span>`;
                tablesHtml += `</div>`;
                
                // 该数据源下的表配置
                tablesHtml += '<table class="compact-table"><thead><tr>';
                tablesHtml += '<th style="width: 200px;">表名</th>';
                tablesHtml += '<th>字段配置</th>';
                tablesHtml += '</tr></thead><tbody>';
                
                for (let i = 0; i < tables.length; i++) {
                    const table = tables[i];
                    tablesHtml += '<tr>';
                    tablesHtml += `<td class="table-name-cell"><strong>${utils.escapeHtml(table.tableName || '未知')}</strong></td>`;
                    
                    if (table.fields && table.fields.length > 0) {
                        let fieldsHtml = '<div class="fields-list">';
                        for (let j = 0; j < table.fields.length; j++) {
                            const field = table.fields[j];
                            const strategyText = field.strategy || '默认策略';
                            const strategyDisplay = field.strategy 
                                ? (field.strategy.length > 25 ? field.strategy.substring(0, 25) + '...' : field.strategy)
                                : '默认';
                            
                            fieldsHtml += '<span class="field-tag" title="字段: ' + utils.escapeHtml(field.fieldName || '-') + '\n策略: ' + utils.escapeHtml(strategyText) + '">';
                            fieldsHtml += `<span class="field-name">${utils.escapeHtml(field.fieldName || '-')}</span>`;
                            if (field.strategy) {
                                fieldsHtml += `<span class="field-strategy" title="加密策略: ${utils.escapeHtml(field.strategy)}">(${utils.escapeHtml(strategyDisplay)})</span>`;
                            } else {
                                fieldsHtml += '<span class="field-strategy default" title="使用默认加密策略">(默认)</span>';
                            }
                            fieldsHtml += '</span>';
                        }
                        fieldsHtml += '</div>';
                        tablesHtml += `<td>${fieldsHtml}</td>`;
                    } else {
                        tablesHtml += '<td><span class="empty-message">无加密字段</span></td>';
                    }
                    
                    tablesHtml += '</tr>';
                }
                
                tablesHtml += '</tbody></table>';
                tablesHtml += `</div>`; // 结束数据源分组
            }
            
            tablesConfigDiv.innerHTML = tablesHtml;
        } else {
            tablesConfigDiv.innerHTML = '<div class="empty-message">未配置表加密规则</div>';
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

    // 绑定 SQL 加密事件
    const encryptSqlBtn = document.getElementById('encryptSqlBtn');
    const clearSqlEncryptBtn = document.getElementById('clearSqlEncryptBtn');
    const sqlEncryptInput = document.getElementById('sqlEncryptInput');
    const copySqlEncryptBtn = document.getElementById('copySqlEncryptBtn');

    if (encryptSqlBtn) {
        encryptSqlBtn.addEventListener('click', () => sqlEncrypt.handleEncryptSql());
    }
    if (clearSqlEncryptBtn) {
        clearSqlEncryptBtn.addEventListener('click', () => sqlEncrypt.clear());
    }
    if (sqlEncryptInput) {
        // 支持 Ctrl+Enter 快捷键
        sqlEncryptInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && e.ctrlKey) {
                sqlEncrypt.handleEncryptSql();
            }
        });
    }
    if (copySqlEncryptBtn) {
        copySqlEncryptBtn.addEventListener('click', () => sqlEncrypt.copyResult());
    }

    // 绑定 SQL 查询事件
    const querySqlBtn = document.getElementById('querySqlBtn');
    const clearSqlQueryBtn = document.getElementById('clearSqlQueryBtn');
    const sqlQueryInput = document.getElementById('sqlQueryInput');

    if (querySqlBtn) {
        querySqlBtn.addEventListener('click', () => sqlQuery.handleQuery());
    }
    if (clearSqlQueryBtn) {
        clearSqlQueryBtn.addEventListener('click', () => sqlQuery.clear());
    }
    if (sqlQueryInput) {
        // 支持 Ctrl+Enter 快捷键
        sqlQueryInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && e.ctrlKey) {
                sqlQuery.handleQuery();
            }
        });
    }

    // 绑定数据初始化事件
    const dataInitEncryptBtn = document.getElementById('dataInitEncryptBtn');
    const dataInitDecryptBtn = document.getElementById('dataInitDecryptBtn');
    const clearDataInitBtn = document.getElementById('clearDataInitBtn');
    const copyDataInitSqlBtn = document.getElementById('copyDataInitSqlBtn');

    if (dataInitEncryptBtn) {
        dataInitEncryptBtn.addEventListener('click', () => dataInit.handleEncrypt());
    }
    if (dataInitDecryptBtn) {
        dataInitDecryptBtn.addEventListener('click', () => dataInit.handleDecrypt());
    }
    if (clearDataInitBtn) {
        clearDataInitBtn.addEventListener('click', () => dataInit.clear());
    }
    if (copyDataInitSqlBtn) {
        copyDataInitSqlBtn.addEventListener('click', () => dataInit.copySql());
    }

    // 初始化标签页
    console.log('准备初始化标签页');
    try {
        tabs.init();
        console.log('标签页初始化完成');
    } catch (error) {
        console.error('标签页初始化失败:', error);
    }

    // 检查登录状态
    login.checkLoginStatus();
    
    // 如果配置信息标签页是活动的，自动加载配置
    const configTab = document.getElementById('configTab');
    if (configTab && configTab.classList.contains('active')) {
        console.log('检测到配置信息标签页是活动的，自动加载配置');
        setTimeout(() => {
            if (typeof configInfo !== 'undefined' && configInfo && typeof configInfo.loadConfig === 'function') {
                configInfo.loadConfig();
            }
        }, 500);
    }
});

