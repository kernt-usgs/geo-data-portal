$(document).ready(function () {
	var tableString = "<table />";
	var trString = "<tr />";
	var thString = "<th />";
	var tdString = "<td />";
	var page = 1;
	
	var reload = function () {
		$('#lastProcessLoad').text(new Date().timeNow());
		$.get('list?page=' + page)
			.done(function (processJSON) {
				$('#processData').empty();
				if (processJSON.length) {
					var $table = $(tableString).appendTo($('#processData'));
					var $headerRow = $(trString).appendTo($table);
					$headerRow.append(
						$(thString).text('Request'),
						$(thString).text('Identifier'),
						$(thString).text('Status'),
						$(thString).text('Created'),
						$(thString).text('Runtime'),
						$(thString).text('Error'));
					$.each(processJSON, function (idx, data) {
						var $dataRow = $(trString).appendTo($table);
						$dataRow.append(
							$(tdString).append($('<a/>').attr('href', data.requestLink).attr('target', '_blank').html('->')),
							$(tdString).text(data.identifier),
							$(tdString).text(data.status),
							$(tdString).text(data.creationTime),
							$(tdString).text(data.elapsedTime),
							$(tdString).text(data.errorMessage));
					});
				} else {
					$('#processData').text("No processes found");
				}
			})
			.fail(function (jqXHR) {
				$('#processData').html(jqXHR.responseText);
			});
	};
	
	$('#loadProcessesButton').click(reload);
	
	$('#previousPageButton').click(function() {
		if (page > 1) {
			page--;
		}
		reload();
	});
	$('#nextPageButton').click(function() {
		page++;
		reload();
	});

	$('#reportButton').click(function () {
		$('#lastReportRun').text(new Date().timeNow());
		$.get('report')
			.done(function (reportJSON) {
				$('#reportData').empty();
				if (reportJSON.algorithms.length) {
					var $table = $(tableString).appendTo($('#reportData'));
					var $headerRow = $(trString).appendTo($table);
					$headerRow.append(
						$(thString).text('Data'),
						$(thString).text('Count'));
					$.each(reportJSON.algorithms, function (idx, data) {
						var $algorithmRow = $(trString).appendTo($table);
						$algorithmRow.addClass('reportHeader').append(
							$(tdString).text(data.identifier),
							$(tdString).text(data.count));
						$.each($(data.dataSets), function (idx, dataset) {
							var $dataSetRow = $(trString).appendTo($table);
							$dataSetRow.append(
								$(tdString).addClass('indented').text(dataset.dataSetURI),
								$(tdString).text(dataset.count));
						});
					});
				} else {
					$('#reportData').text("Nothing to report on");
				}
			})
			.fail(function (jqXHR) {
				$('#reportData').html(jqXHR.responseText);
			});
	});
});

Date.prototype.timeNow = function () {
	var year = this.getFullYear();
	var month = zeroPadSingleDigit(this.getMonth() + 1);
	var day = zeroPadSingleDigit(this.getDate());
	var hours = zeroPadSingleDigit(this.getHours());
	var minutes = zeroPadSingleDigit(this.getMinutes());
	var seconds = zeroPadSingleDigit(this.getSeconds());
	return year + "-" + month + "-" + day + " " + hours + ":" + minutes + ":" + seconds;
};

function zeroPadSingleDigit (value) {
	return value < 10 ? "0" + value : value;
}