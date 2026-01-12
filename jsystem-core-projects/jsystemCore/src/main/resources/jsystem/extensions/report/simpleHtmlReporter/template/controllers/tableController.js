/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

function collectAllScenarios(){
    var scenarios = new Array();
    var index = 1;
    $(execution.machines).each(function(){
        $(this.children).each(function() {
            scenarios.push(this);
        });
    });
    return scenarios;
}

function appendScenariosToTable(scenarios, table){
    $(scenarios).each(function(){
       var tr = $('<tr>');
       tr.append($('<td>').text(this.timestamp));
       var a = $("<a>").text(this.name).attr("href", "scenarios/" + this.name + "_" + this.uid + "/scenario.html").attr("target", "_blank");
       tr.append($('<td>').append(a));
       tr.append($('<td>').text(this.scenarioProperties ? this.scenarioProperties.sutFile : ''));
       tr.append($('<td>').text(this.status).addClass('s_' + this.status.toLowerCase() + "_back"));
       tr.append($('<td>').text(this.duration));
       $(table).append(tr);
    });
}

function tableController(element) {
    var scenarios = collectAllScenarios();
    appendScenariosToTable(scenarios, element);
}


