/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

function collectAllScenarios() {
    var scenarios = [];
    if (typeof execution === 'undefined' || !execution.machines) {
        return scenarios;
    }
    
    $(execution.machines).each(function() {
        if (this.children) {
            $(this.children).each(function() {
                if (this.type === 'scenario') {
                    scenarios.push(this);
                }
            });
        }
    });
    
    return scenarios;
}

function appendScenariosToSumTable(scenarios, table) {
    $(scenarios).each(function() {
        var tr = $('<tr>');
        
        // Timestamp column
        tr.append($('<td>').text(this.timestamp || ''));
        
        // Scenario name column (with link to tree view)
        var a = $("<a>").text(this.name).attr("href", "tree.html?node=" + this.name);
        tr.append($('<td>').addClass('scenario-column').append(a));
        
        // Sut File column
        var sutFile = '';
        if (this.scenarioProperties && this.scenarioProperties.sutFile) {
            sutFile = this.scenarioProperties.sutFile;
        }
        tr.append($('<td>').text(sutFile));
        
        // Status column (in uppercase with color)
        var status = this.status || '';
        var statusUpper = status.toUpperCase();
        var statusClass = '';
        switch (status) {
            case "success":
                statusClass = "s_success_back";
                break;
            case "error":
                statusClass = "s_error_back";
                break;
            case "failure":
                statusClass = "s_failure_back";
                break;
            case "warning":
                statusClass = "s_warning_back";
                break;
        }
        tr.append($('<td>').text(statusUpper).addClass(statusClass));
        
        // Duration column
        tr.append($('<td>').text(this.duration || ''));
        
        $(table).append(tr);
    });
}

function sumTableController(element) {
    var scenarios = collectAllScenarios();
    appendScenariosToSumTable(scenarios, element);
}
