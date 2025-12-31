/**
 * Save Employee Data and Upload File
 * This function saves employee data via APEX process and uploads a file via REST service
 */
function saveEmployee() {
    // Get file input element
    var fileInput = document.getElementById('P4_UPLOAD_IMAGE');

    // Validate file input
    if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
        apex.message.alert('Please select a file');
        return;
    }

    // Extract file information
    var file = fileInput.files[0];
    var filename = file.name;
    var mimetype = file.type; // Get MIME type from file object
    var app_cs;

    // Call APEX server process to save employee data
    apex.server.process(
        'SAVE_DATA_V2',
        {
            x01: ('P4_ENAME'),      // Employee Name
            x02: ('P4_HIREDATE'),   // Hire Date
            x03: ('P4_SAL'),        // Salary
            x04: ('pFlowId'),       // Application ID
            x05: ('pInstance'),     // Session ID
            x06: filename,            // Original Filename
            x07: mimetype             // MIME Type
        },
        {
            dataType: 'json',
            success: function(pData) {
                // Check if save was successful
                if (pData.status !== 'SUCCESS') {
                    apex.message.alert(pData.message || 'Save failed');
                    return;
                }

                // ✅ Set Employee Number in form
                ('P4_EMPNO', pData.id);
                app_cs = pData.cs;
                
                // ✅ Upload file to REST service
                uploadFromApex(pData.id, app_cs);
            },
            error: function(jqXHR, textStatus, errorThrown) {
                // Log error and show alert
                console.error('APEX Process Error:', textStatus, errorThrown);
                apex.message.alert('Error saving employee data');
            }
        }
    );
}

/**
 * Upload File to REST Service
 * @param {number} empId - Employee ID from APEX process
 * @param {string} app_cs - Application CS token from APEX process
 */
function uploadFromApex(empId, app_cs) {
    // Get file input element
    var fileInput = document.getElementById('P4_UPLOAD_IMAGE');

    // Validate file input
    if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
        apex.message.alert('Please select a file');
        return;
    }

    // Extract file
    var file = fileInput.files[0];
    var reader = new FileReader();
    
    // Get session information
    var appId = app_cs;        // Application CS token
    var sessionId = ('pInstance'); // Session ID

    // File reader load event handler
    reader.onload = function() {
        // Create XMLHttpRequest
        var xhr = new XMLHttpRequest();
        var restEndpoint = 'http://172.16.250.162:9001/rest_token/SaveDocumentV2';
        
        // Initialize POST request
        xhr.open('POST', restEndpoint, true);

        // Set request headers for REST service
        xhr.setRequestHeader('Content-Type', 'application/octet-stream');
        xhr.setRequestHeader('X-Doc-Id', empId);
        xhr.setRequestHeader('X-File-Name', file.name);
        xhr.setRequestHeader('X-Doc-Type', 'EMP_DOC');
        xhr.setRequestHeader('p_app_id', appId);
        xhr.setRequestHeader('p_session_id', sessionId);

        // Handle successful response
        xhr.onload = function() {
            if (xhr.status === 200) {
                // Show success message
                apex.message.showPageSuccess(
                    'Employee saved and file uploaded successfully'
                );
            } else {
                // Show error message with response text
                apex.message.alert('Upload failed: ' + xhr.responseText);
            }
        };

        // Handle network errors
        xhr.onerror = function() {
            apex.message.alert('Network error during upload');
        };

        // Send file data
        xhr.send(reader.result);
    };

    // Start reading file as ArrayBuffer
    reader.readAsArrayBuffer(file);
}

// Execute saveEmployee function
saveEmployee();
