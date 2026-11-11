(function () {
    'use strict';

    const MAX_QUANTITY = 999999999999999;
    const MAX_FILE_BYTES = 5 * 1024 * 1024;
    const COMMUNICATION_ERROR = '서버와 통신할 수 없습니다.';

    const state = {
        fileName: '-',
        dirty: false,
        changedCount: 0,
        unexported: false,
        items: [],
        busy: false
    };

    const el = {
        fileName: document.getElementById('file-name'),
        fileInput: document.getElementById('file-input'),
        uploadButton: document.getElementById('upload-button'),
        discardButton: document.getElementById('discard-button'),
        saveButton: document.getElementById('save-button'),
        downloadButton: document.getElementById('download-button'),
        dirtyStatus: document.getElementById('dirty-status'),
        unexportedStatus: document.getElementById('unexported-status'),
        notice: document.getElementById('notice'),
        rows: document.getElementById('item-rows')
    };

    // ---------- 서버 호출 ----------

    class ApiError extends Error {
    }

    async function request(path, options) {
        let response;
        try {
            response = await fetch(path, options);
        } catch (e) {
            throw new ApiError(COMMUNICATION_ERROR);
        }
        if (!response.ok) {
            let message = '요청을 처리하지 못했습니다.';
            try {
                const body = await response.json();
                if (body && body.message) {
                    message = body.message;
                }
            } catch (e) {
                // 본문이 JSON이 아니면 기본 메시지를 쓴다.
            }
            throw new ApiError(message);
        }
        return response;
    }

    async function requestJson(path, options) {
        const response = await request(path, options);
        return response.json();
    }

    function postJson(path, body) {
        return requestJson(path, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(body)
        });
    }

    // ---------- 상태 반영 ----------

    function applyList(data) {
        state.fileName = data.fileName;
        state.dirty = data.dirty;
        state.changedCount = data.changedCount;
        state.unexported = data.unexported;
        state.items = data.items;
    }

    function applyChange(data) {
        const index = state.items.findIndex(function (item) {
            return item.id === data.item.id;
        });
        if (index >= 0) {
            state.items[index] = data.item;
        }
        state.dirty = data.dirty;
        state.changedCount = data.changedCount;
        state.unexported = data.unexported;
    }

    // ---------- 화면 그리기 ----------

    function formatNumber(value) {
        return Number(value).toLocaleString('ko-KR');
    }

    function formatDateTime(value) {
        if (!value) {
            return '-';
        }
        return value.replace('T', ' ').substring(0, 16);
    }

    function cell(text, className) {
        const td = document.createElement('td');
        td.textContent = text;
        if (className) {
            td.className = className;
        }
        return td;
    }

    function quantityCell(item) {
        const td = document.createElement('td');
        td.className = 'num quantity-change';
        if (item.changed) {
            td.appendChild(document.createTextNode(formatNumber(item.savedQuantity) + ' '));
            const arrow = document.createElement('span');
            arrow.className = 'arrow';
            arrow.textContent = '→';
            td.appendChild(arrow);
            td.appendChild(document.createTextNode(' ' + formatNumber(item.quantity)));
        } else {
            td.textContent = formatNumber(item.quantity);
        }
        return td;
    }

    function changeForm(item, draftValue) {
        const td = document.createElement('td');
        const form = document.createElement('div');
        form.className = 'change-form';

        const input = document.createElement('input');
        input.type = 'text';
        input.inputMode = 'numeric';
        input.autocomplete = 'off';
        input.placeholder = '수량';
        input.value = draftValue || '';
        input.dataset.itemId = String(item.id);
        input.setAttribute('aria-label', item.name + ' 수량');
        input.addEventListener('input', function () {
            input.value = input.value.replace(/[^0-9]/g, '');
        });

        const inbound = document.createElement('button');
        inbound.type = 'button';
        inbound.textContent = '입고';
        inbound.dataset.role = 'change';
        inbound.addEventListener('click', function () {
            changeStock(item, 'inbound', '입고', input);
        });

        const outbound = document.createElement('button');
        outbound.type = 'button';
        outbound.textContent = '출고';
        outbound.dataset.role = 'change';
        outbound.addEventListener('click', function () {
            changeStock(item, 'outbound', '출고', input);
        });

        form.append(input, inbound, outbound);
        td.appendChild(form);
        return td;
    }

    function currentDrafts() {
        const drafts = {};
        el.rows.querySelectorAll('input[data-item-id]').forEach(function (input) {
            drafts[input.dataset.itemId] = input.value;
        });
        return drafts;
    }

    function render() {
        const drafts = currentDrafts();
        el.fileName.textContent = state.fileName;
        el.rows.replaceChildren();

        if (state.items.length === 0) {
            const tr = document.createElement('tr');
            const td = cell('품목이 없습니다.', 'empty');
            td.colSpan = 5;
            tr.appendChild(td);
            el.rows.appendChild(tr);
        }
        state.items.forEach(function (item) {
            const tr = document.createElement('tr');
            if (item.changed) {
                tr.className = 'changed';
            }
            tr.append(
                cell(String(item.id)),
                cell(item.name),
                quantityCell(item),
                cell(formatDateTime(item.updatedAt)),
                changeForm(item, drafts[String(item.id)])
            );
            el.rows.appendChild(tr);
        });

        el.dirtyStatus.hidden = !state.dirty;
        el.dirtyStatus.textContent = '저장하지 않은 변경이 ' + state.changedCount + '건 있습니다.';
        el.unexportedStatus.hidden = !state.unexported;
        el.saveButton.textContent = state.changedCount > 0 ? '저장 (' + state.changedCount + ')' : '저장';
        el.downloadButton.classList.toggle('btn-primary', state.unexported);
        updateControls();
    }

    function updateControls() {
        const busy = state.busy;
        el.saveButton.disabled = busy || !state.dirty;
        el.discardButton.disabled = busy || !state.dirty;
        el.downloadButton.disabled = busy;
        el.fileInput.disabled = busy;
        el.uploadButton.disabled = busy || el.fileInput.files.length === 0;
        el.rows.querySelectorAll('button, input').forEach(function (control) {
            control.disabled = busy;
        });
    }

    function showNotice(type, message) {
        el.notice.textContent = message;
        el.notice.className = 'notice ' + type;
        el.notice.hidden = false;
    }

    function hideNotice() {
        el.notice.hidden = true;
        el.notice.textContent = '';
    }

    // ---------- 동작 ----------

    /** 요청이 진행되는 동안 모든 버튼을 막고, 실패하면 서버 메시지를 알림에 보인다. */
    async function run(action) {
        if (state.busy) {
            return;
        }
        state.busy = true;
        updateControls();
        try {
            await action();
        } catch (e) {
            showNotice('error', e instanceof ApiError ? e.message : COMMUNICATION_ERROR);
        } finally {
            state.busy = false;
            updateControls();
        }
    }

    function parseQuantity(text) {
        if (!/^[0-9]+$/.test(text)) {
            return null;
        }
        const value = Number(text);
        return value <= MAX_QUANTITY ? value : null;
    }

    function changeStock(item, kind, label, input) {
        const quantity = parseQuantity(input.value.trim());
        if (quantity === null) {
            showNotice('error', '수량은 0 이상 ' + formatNumber(MAX_QUANTITY) + ' 이하의 정수로 입력해 주세요.');
            input.focus();
            return;
        }
        run(async function () {
            const data = await postJson('/api/items/' + item.id + '/' + kind, {quantity: quantity});
            applyChange(data);
            input.value = '';
            render();
            showNotice('success', data.item.name + ' ' + formatNumber(quantity) + '개 ' + label + ' 반영 (저장 전)');
        });
    }

    function save() {
        run(async function () {
            const data = await requestJson('/api/items/save', {method: 'POST'});
            applyList(data);
            render();
            if (data.savedCount === 0) {
                showNotice('success', '저장할 변경이 없습니다.');
            } else {
                showNotice('success', data.savedCount + '건 저장 완료. 파일로 받으려면 [다운로드]를 누르세요.');
            }
        });
    }

    function discard() {
        if (!window.confirm('저장하지 않은 변경 ' + state.changedCount + '건을 모두 버립니다. 계속하시겠습니까?')) {
            return;
        }
        run(async function () {
            applyList(await requestJson('/api/items/discard', {method: 'POST'}));
            render();
            showNotice('success', '저장하지 않은 변경을 버렸습니다.');
        });
    }

    function onFileSelected() {
        const file = el.fileInput.files[0];
        if (file) {
            if (!file.name.toLowerCase().endsWith('.xlsx')) {
                el.fileInput.value = '';
                showNotice('error', '엑셀 파일(.xlsx)만 첨부할 수 있습니다.');
            } else if (file.size > MAX_FILE_BYTES) {
                el.fileInput.value = '';
                showNotice('error', '파일 크기는 5MB 이하여야 합니다.');
            }
        }
        updateControls();
    }

    function upload() {
        const file = el.fileInput.files[0];
        if (!file) {
            return;
        }
        let message = '현재 재고 데이터가 선택한 파일의 내용으로 바뀝니다.';
        if (state.dirty) {
            message += '\n저장하지 않은 변경 ' + state.changedCount + '건도 사라집니다.';
        }
        if (state.unexported) {
            message += '\n내려받지 않은 저장 내용도 사라집니다.';
        }
        if (!window.confirm(message + '\n계속하시겠습니까?')) {
            return;
        }
        run(async function () {
            const form = new FormData();
            form.append('file', file);
            const data = await requestJson('/api/items/upload', {method: 'POST', body: form});
            applyList(data);
            el.fileInput.value = '';
            render();
            showNotice('success', data.importedCount + '개 품목을 불러왔습니다.');
        });
    }

    function fileNameFrom(response) {
        const header = response.headers.get('Content-Disposition') || '';
        const match = /filename\*=UTF-8''([^;]+)/i.exec(header);
        if (match) {
            try {
                return decodeURIComponent(match[1]);
            } catch (e) {
                // 디코딩에 실패하면 기본 이름을 쓴다.
            }
        }
        return 'inventory.xlsx';
    }

    function download() {
        if (state.dirty && !window.confirm(
            '저장하지 않은 변경 ' + state.changedCount + '건은 파일에 포함되지 않습니다. 계속하시겠습니까?')) {
            return;
        }
        run(async function () {
            const response = await request('/api/items/download');
            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = fileNameFrom(response);
            document.body.appendChild(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);

            applyList(await requestJson('/api/items'));
            render();
            showNotice('success', '파일을 내려받았습니다.');
        });
    }

    function load() {
        run(async function () {
            applyList(await requestJson('/api/items'));
            render();
        });
    }

    // ---------- 시작 ----------

    el.saveButton.addEventListener('click', save);
    el.discardButton.addEventListener('click', discard);
    el.downloadButton.addEventListener('click', download);
    el.uploadButton.addEventListener('click', upload);
    el.fileInput.addEventListener('change', function () {
        hideNotice();
        onFileSelected();
    });
    window.addEventListener('beforeunload', function (event) {
        if (state.dirty || state.unexported) {
            event.preventDefault();
            event.returnValue = '';
        }
    });

    load();
})();
