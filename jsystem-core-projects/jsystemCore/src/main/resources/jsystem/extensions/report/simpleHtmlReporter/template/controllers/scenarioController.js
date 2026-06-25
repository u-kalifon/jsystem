var depthStep = 20;
var levelsStack = [];

Array.prototype.top = function(){
    return this.length > 0 ? this[this.length-1] : undefined;
};

function setFixedProperties(test, element) {
    $(element).find("#name").text(test.name);
    // var $statusElement = $(element).find("#status");
    $(element).find("#status").text(test.status);
    addStatusBackgroundClass($(element).find("#status"), test);
    addPropertiesToTbl(test.scenarioProperties, $(element).find("#propTbl > tbody"));
    $(element).find("#timestamp").text(test.timestamp);
    $(element).find("#description").html(nl2br(he.escape(test.description || "")));
}

function addPropertiesToTbl(properties, table) {
    for (var key in properties) {
        var tr = $('<tr>');
        tr.append($('<td>').text(key));
        var value = properties[key];
        var lines = value.split(/\\r?\\n/);
        var td = $('<td>');
        lines.forEach(function(line) {
            td.append($('<div>').text(line));
        });
        tr.append(td);
        $(table).append(tr);
    }
}

function isPropertyExist(element, property) {
    return (element.hasOwnProperty(property) && element[property] !== null && element[property] !== "");
}

function createStatusIcon(status) {
    var $icon = $("<span>").addClass("status-icon").attr("aria-hidden", "true");
    switch (status) {
        case "success":
            $icon.addClass("status-icon-success").html(
                '<svg viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg">' +
                '<path d="M6.5 11.5 3 8l1-1 2.5 2.5L12 4l1 1-6.5 6.5z" fill="currentColor"/>' +
                '</svg>');
            break;
        case "failure":
        case "error":
            $icon.addClass("status-icon-failure").html(
                '<svg viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg">' +
                '<path d="M4.5 4.5 11.5 11.5M11.5 4.5 4.5 11.5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>' +
                '</svg>');
            break;
        case "warning":
            $icon.addClass("status-icon-warning").html(
                '<svg viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg">' +
                '<path d="M8 2 14 13H2L8 2z" fill="currentColor"/>' +
                '<path d="M8 6.5v3.5M8 12h.01" stroke="#fff" stroke-width="1.5" stroke-linecap="round"/>' +
                '</svg>');
            break;
        default:
            return null;
    }
    return $icon;
}

function addStatusAsClass(elementToAppend, elementWithStatus, suffix, showStatusIcon) {
    if (isPropertyExist(elementWithStatus, "status")) {
        elementToAppend.addClass("s_" + elementWithStatus.status.toLowerCase() + suffix);
        if (showStatusIcon) {
            var $icon = createStatusIcon(elementWithStatus.status.toLowerCase());
            if ($icon) {
                var $timestamp = elementToAppend.children(".timestamp").first();
                if ($timestamp.length) {
                    $icon.insertAfter($timestamp);
                } else {
                    elementToAppend.prepend($icon);
                }
            }
        }
    }
}

function addStatusBackgroundClass(elementToAppend, elementWithStatus) {
    if (isPropertyExist(elementWithStatus, "status")) {
        elementToAppend.addClass("s_" + elementWithStatus.status.toLowerCase() +"_back");
    }
}

function setRegularElement($container, element, isHtml) {
    var $div = $("<div>");
    var $timestamp = $("<span>").addClass('timestamp').text(element.time ? element.time.split(' ')[1] : '');

    if (isPropertyExist(element, "message")) {
        var $content = $("<span>").addClass('innerToggle').text(element.title);
        indent($content);
        $div.append($timestamp).append($content);

        // add inner div with the message
        var $innerDiv = $("<div>").addClass('innerContent').html(nl2br(element.message));
        $innerDiv.css("margin-left", (levelsStack.length + 1) * depthStep + "px");
        $div.append($innerDiv);
    }
    else{
        var $content = isHtml ? $("<span>").html(element.title) :  $("<span>").text(element.title);
        indent($content);
        $div.append($timestamp).append($content);
    }

    addStatusAsClass($div, element, "_text", true);
    appendElement($container, $div);
}

function setStartLevelElement($container, element) {
    setCollapsableElement($container, element, 'startLevel');   // will push the div into the level stack
}

function setStartScenarioElement($container, element) {
    setCollapsableElement($container, element, 'startScenario', true);
}

function setStepElement($container, element) {
    var $timestamp = $("<span>").addClass('timestamp').text(element.time ? element.time.split(' ')[1] : '');
    var $content = $("<span>").addClass('step').addClass("innerToggle").addClass("closed").text(element.title);
    indent($content);
    var $div = $("<div>").append($timestamp).append($content);

    if (isPropertyExist(element, "userDoc")) {
        var $userDocDiv = $("<div>").addClass('innerContent userDocContent').html(nl2br(element.userDoc));
        $userDocDiv.css("margin-left", (levelsStack.length + 1) * depthStep + "px");
        $div.append($userDocDiv);
    }

    if (isPropertyExist(element, "properties")) {
        // create a table with the properties
        var $table = $("<table>").addClass('smallTbl');
        var $tbody = $("<tbody>");
        $table.append($tbody);
        addPropertiesToTbl(element.properties, $tbody);

        // add inner div with the table
        var $innerDiv = $("<div>").addClass('innerContent').append($table);
        $innerDiv.css("margin-left", (levelsStack.length + 1) * depthStep + "px");
        $div.append($innerDiv);
    }

    addStatusAsClass($div, element, "_step");
    appendElement($container, $div);
}

function setCollapsableElement($container, element, className, startExpanded) {
    var $timestamp = $("<span>").addClass('timestamp').text(element.time ? element.time.split(' ')[1] : '');
    var $content = $("<span>").addClass(className).addClass("innerToggle").text(element.title);
    if (!startExpanded) {
        $content.addClass("closed");
    }
    var $div = $("<div>").append($timestamp).append($content);
    indent($content);

    if (isPropertyExist(element, "userDoc")) {
        var $userDocDiv = $("<div>").addClass('innerContent userDocContent').html(nl2br(element.userDoc));
        $userDocDiv.css("margin-left", (levelsStack.length + 1) * depthStep + "px");
        $div.append($userDocDiv);
    }

    addStatusAsClass($div, element, "_text", className === "startLevel");
    appendElement($container, $div);

    // push the div into the level stack
    levelsStack.push($div);
}

function setStopLevelElement(element) {
    if(!$.isEmptyObject(levelsStack)){
        levelsStack.pop();
    }
}

function setStopScenarioElement(element) {
    if(!$.isEmptyObject(levelsStack)){
        levelsStack.pop();
    }
}

function setImageElement($container, element){
    var $timestamp = $("<span>").addClass('timestamp').text(element.time ? element.time.split(' ')[1] : '');
    var $div = $("<div>");

    var $img = $("<img>").attr("src",element.message).addClass("example-image").attr("alt",element.title);
    $img.on('load', function() {
        var natW = this.naturalWidth;
        var natH = this.naturalHeight;
        if (natW < 600 && natH < 400) {
            $(this).css({ width: natW + 'px', height: natH + 'px' });
        } else {
            var scale = Math.min(1, Math.max(600 / natW, 400 / natH));
            $(this).css({ width: Math.round(natW * scale) + 'px', height: Math.round(natH * scale) + 'px' });
        }
    });
    var $a = $("<a>").attr("href",element.message).attr("data-lightbox","image-1").attr("title",element.title);
    $a.append($img);
    indent($a);
    $div.append($timestamp).append($a);

    addStatusAsClass($div, element, "_text");
    appendElement($container, $div);
}

function setLinkElement($container, element) {
    var $timestamp = $("<span>").addClass('timestamp').text(element.time ? element.time.split(' ')[1] : '');
    var $div = $("<div>").append($timestamp);
    var $content;

    if (isPropertyExist(element, "message")) {
        $content = $('<a>').text(element.title).attr("href", element.message).attr("target", "_blank");
    }
    else{
        $content = $("<div>").text(element.title);
    }

    indent($content);
    $div.append($content);
    addStatusAsClass($div, element, "_text");
    appendElement($container, $div);
}

/**
 * Should $element be appended to the div, or as a sub-item
 * @param $table
 * @param $element
 */
function appendElement($table, $element){
    if(levelsStack.length == 0){
        // stack is empty, append element directly to the main div
        $table.append($element);
    }
    else{
        // append the element to the "top" item in the stack
        levelsStack.top().append($element);
    }
}

function indent($element){
    $element.css("margin-left", levelsStack.length * depthStep + "px");
}

function indentAttr($element){
    $element.css("margin-left", 100 + levelsStack.length * depthStep + "px");
}

function nl2br(str){
    const breakTag = "<br/>";
    return (str + '').replace(/([^>\r\n]?)(\r\n|\n\r|\r|\n)/g, '$1'+ breakTag +'$2');
}

function setReportElements($container, reportElements) {

    $(reportElements).each(function() {
        switch (this.type) {
            case "startLevel":
                setStartLevelElement($container, this);
                break;
            case "stopLevel":
                setStopLevelElement(this);
                break;
            case "startScenario":
                setStartScenarioElement($container, this);
                break;
            case "endScenario":
                setStopScenarioElement(this);
                break;
            case "lnk":
                setLinkElement($container, this);
                break;
            case "step":
                // if the step is hidden in html, and the status is success, don't set the step element
                if (!(isPropertyExist(this, "properties") && this.properties.isHiddenInHtml === "true" && this.status === "SUCCESS")) {
                    setStepElement($container, this);
                }
                break;
            case "img":
                setImageElement($container,this);
                break;
            case "html":
                setRegularElement($container, this, true);
                break;
            default:
                setRegularElement($container, this ,false);
                break;
        }
    });

    prepareLevels($container);
}

function testController(element) {
    setFixedProperties(test, element);
    setReportElements($(element).find("#detailsDiv"), test.reportElements);
}

function prepareLevels($container) {
    // GUI enhancement- find spans with "startLevel" class that have no div siblings (i.e. have no content between 'startLevel' and 'stopLevel'),
    // and replace class with "emptyStartLevel"
    // This way we won't try to click the blue link, and they will always be "expanded"
    $(".startLevel").each(function(i,e){
        if($(e).siblings('div').length == 0){
            $(e).removeClass('startLevel').addClass('emptyStartLevel');
        }
    });

    // Same for startScenario - empty scenario has no div siblings between startScenario and endScenario
    $(".startScenario").each(function(i,e){
        if($(e).siblings('div').length == 0){
            $(e).removeClass('startScenario').addClass('emptyStartScenario');
        }
    });

    // Scenarios are expanded by default - show their content (sibling divs of the header span)
    $(".startScenario").siblings('div').show();

    // register the 'click' on 'startLevel', 'startScenario', 'step' and 'innerToggle' elements
    $(".startLevel, .startScenario, .step, .innerToggle").click(function(){
        $(this).toggleClass("closed").parent().children('div').not('.userDocContent').toggle('fast');
    });

    // register the 'click' on ExpandAll and CollapseAll
    $("#detailsDivExpandAll").click(function(){
        $(".startLevel, .startScenario, .step, .innerToggle").removeClass('closed').parent().children('div').show('fast');

    });
    $("#detailsDivCollapseAll").click(function(){
        $(".startLevel, .startScenario, .step, .innerToggle").addClass('closed').parent().children('div').not('.userDocContent').hide('fast');
    });

}
