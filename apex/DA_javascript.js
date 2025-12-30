function saveEmployee() {
    var fileInput = document.getElementById('P4_UPLOAD_IMAGE');

    if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
        apex.message.alert('Please select a file');
        return;
    }

    var file = fileInput.files[0];
    var filename = file.name;
    var mimetype = file.type; // Get the MIME type from the file object

    apex.server.process(
        'SAVE_DATA_V2',
        {
            x01: ('P4_ENAME'),
            x02: ('P4_HIREDATE'),
            x03: ('P4_SAL'),
            x04: ('pFlowId'),
            x05: ('pInstance'),
            x06: filename,
            x07: mimetype // Send MIME type to APEX process
        },
        {
            dataType: 'json',
            success: function (pData) {
                if (pData.status !== 'SUCCESS') {
                    apex.message.alert(pData.message || 'Save failed');
                    return;
                }

                // ✅ SET EMPNO
                ('P4_EMPNO', pData.id);

                // ✅ NOW upload file
                uploadFromApex(pData.id);
            },
            error: function (jqXHR, textStatus, errorThrown) {
                console.error(textStatus, errorThrown);
                apex.message.alert('Error saving data');
            }
        }
    );
}

function uploadFromApex(empId) {

    var fileInput = document.getElementById('P4_UPLOAD_IMAGE');

    if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
        apex.message.alert('Please select a file');
        return;
    }

    var file = fileInput.files[0];

    var reader = new FileReader();
    var appId = ('pFlowId');
    var sessionId = ('pInstance');
    reader.onload = function () {

        var xhr = new XMLHttpRequest();
        xhr.open(
            'POST',
            'http://weblogic_ip_address:port/rest_token/SaveDocumentV2',
            true
        );

        xhr.setRequestHeader('Content-Type', 'application/octet-stream');
        xhr.setRequestHeader('X-Doc-Id', empId);
        xhr.setRequestHeader('X-File-Name', file.name);
        xhr.setRequestHeader('X-Doc-Type','EMP_DOC');
        xhr.setRequestHeader('p_app_id',appId);
        xhr.setRequestHeader('p_session_id',sessionId);

        xhr.onload = function () {
            if (xhr.status === 200) {
                apex.message.showPageSuccess(
                  'Employee saved and file uploaded successfully'
                );
            } else {
                apex.message.alert('Upload failed: ' + xhr.responseText);
            }
        };

        xhr.onerror = function () {
            apex.message.alert('Network error during upload');
        };

        xhr.send(reader.result);
    };

    reader.readAsArrayBuffer(file);
}


saveEmployee();
