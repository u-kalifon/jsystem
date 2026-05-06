/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

function collectScenariosForSumTable() {
    var scenarios = [];
    if (typeof execution === 'undefined' || !execution.machines) {
        return scenarios;
    }
    
    $.each(execution.machines, function(index, machine) {
        if (machine.children) {
            $.each(machine.children, function(index, child) {
                scenarios.push(child);
            });
        }
    });
    
    return scenarios;
}

function appendScenariosToSumTable(scenarios, table) {
    $.each(scenarios, function(index, scenario) {
        var tr = $('<tr>');
        
        // Timestamp column
        tr.append($('<td>').text(scenario.timestamp || ''));
        
        // Scenario name column (with link to tree view)
        var a = $("<a>").text(scenario.name).attr("href", "scenarios/" + scenario.name + "_" + scenario.uid + "/scenario.html").attr("target", "_blank");
        tr.append($('<td>').addClass('scenario-column').append(a));
        
        // Sut File column
        var sutFile = '';
        if (scenario.scenarioProperties && scenario.scenarioProperties.sutFile) {
            sutFile = scenario.scenarioProperties.sutFile;
        }
        tr.append($('<td>').text(sutFile));
        
        // Status column (in uppercase with color)
        var status = scenario.status || '';
        var statusClass = '';
        switch (status) {
            case "SUCCESS":
                statusClass = "s_success_back";
                break;
            case "ERROR":
                statusClass = "s_error_back";
                break;
            case "FAILURE":
                statusClass = "s_failure_back";
                break;
            case "WARNING":
                statusClass = "s_warning_back";
                break;
        }
        tr.append($('<td>').text(status).addClass(statusClass));
        
        // Duration column
        tr.append($('<td>').text(scenario.duration || ''));
        
        $(table).find('tbody').append(tr);
    });
}

function sumTableController(element) {
    var scenarios = collectScenariosForSumTable();
    appendScenariosToSumTable(scenarios, element);
}
