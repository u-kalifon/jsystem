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

function statusBarsController(bars){
    var scenarios = collectAllScenarios();
    if (scenarios.length == 0) {
    	return;
    }
    var success = 0;
    var failure = 0;
    var warning = 0;
    
    $(scenarios).each(function() {
        switch (this.status) {
            case "success":
                success++;
                break;
            case "error":
                failure++;
                break;
            case "failure":
                failure++;
                break;
            case "warning":
                warning++;
                break;
        }
    });

    function calculatePercent(part) {
    	var percent = part / scenarios.length * 100;
    	return percent + "%";
    };

    function renderPercentageText(part) {
    	var percent = part / scenarios.length * 100;
    	if (percent > 0 && percent < 1) {
    		return ".";
    	}
   		percent = Math.round(percent);
        if (percent <= 2) {
            return percent;
        }
        else if (percent <= 5){
            return percent +"%";
        } else {
            return percent + "% (" + part + " of " + scenarios.length + ")";
        }
    }
    
    $(".success").animate({
        width: calculatePercent(success)
    },100).text(renderPercentageText(success));
    $(".failure").animate({
        width: calculatePercent(failure)
    }, 100).text(renderPercentageText(failure));
    $(".warning").animate({
        width: calculatePercent(warning)
    }, 100).text(renderPercentageText(warning));
}
