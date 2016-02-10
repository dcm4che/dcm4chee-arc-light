$(function() {
	//TODO make an angular solution for that
	$(".toggle-button,ul.nav a").click(function(){
		$("ul.nav").toggle(300);
		$(".toggle-button.out").toggle(100);
	});
	//Nice to have: detect different shortcuts, key typping
	// $(document).keypress(function(e){
	// 	console.log("e=",e);
	// 	console.log("e.which=",e.which);
	// 	console.log("e.ctrlKey=",e.ctrlKey);
	// 	console.log("e.key=",e.key);

	//     // var checkWebkitandIE=(e.which==26 ? 1 : 0);
	//     // var checkMoz=(e.which==122 && e.ctrlKey ? 1 : 0);

	//     // if (checkWebkitandIE || checkMoz) $("body").append("<p>ctrl+z detected!</p>");
	// });
});
