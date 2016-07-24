// JavaScript Document
      /*对于非标准的浏览器，JS获取的Agent与服务器端接收到的Agent不一致
      var agent=window.navigator.userAgent;
      var i=agent.indexOf("; .NET");
      if (i!=-1) {//JS读取IE的Agent会附带.NET ....的一些信息，但发送到服务器时又没有，这里需要进行一致性处理
        var j=agent.lastIndexOf(";");
        if (j!=-1) {
            agent=agent.substring(0, i)+agent.substring(j);
        }
      }*/
	  
(function($){

	$.init=function(token, tokenName, seedName) {
		$(document).data("token", token).data("tokenName", tokenName).data("seedName", seedName);
	};
	
	$.validate=function() {
		var sample="$().##(IIll11)OO|I|l|WWMMX000XY*YYY+YY-00000+";
		var str=(arguments.length<sample.length ? sample.substring(0, sample.length-arguments.length) : sample)+sample;
		for (var i=0; i<arguments.length; i++) {
			str+=arguments[i]+""+i;//注意不能去掉（+""+），否则会引起数字相加。
		}
		return sha256(str);
	};
	
	var _ajax=$.ajax;
	var _ajax_error;
	var _ajax_success;
	
	var _new_ajax_error=function(XHR, data, status) {
		data=data.toString();
		if (_ajax_error)  {
			_ajax_error(XHR, data, status);
		} else {
			return;
			/**因为有时AJAX会出现abort错误，这类错误类忽略，所以就不显示服务器端错误信息
			var msg="";
			if (data=="timeout") {
				msg="服务器响应超时";
			} else {
				msg="服务器端发生错误";
			}
			try {
				APPL.ajaxError(msg);
			} catch (e) {
				alert("服务器端发生错误");//（可定义APPL.ajaxError(data, status)进行处理）
			}*/
		}
	};
	
	var _new_ajax_success=function(data, status, XHR) {
		data=data.toString();
		if (data.indexOf("RESULT_MSG:LOGIN, //tadAppCloud MVC Result Message")!=-1) {
			try {
				APPL.ajaxLogin(data, status);
			} catch (e) {
				_new_ajax_error(XHR, "登录已经超时，请重新登录", status);
			}
		} else {
			var msg="";
			if (data.indexOf("RESULT_MSG:CSRF, //tadAppCloud MVC Result Message")!=-1) {
				msg="请求无效（可能是服务器错误）";
			} else if (data.indexOf("RESULT_MSG:404, //tadAppCloud MVC Result Message")!=-1) {
				msg="请求错误（页面不存在）";
			} else if (data.indexOf("RESULT_MSG:500, //tadAppCloud MVC Result Message")!=-1) {
				msg="服务器内部错误";
			} else {
				msg="";
			}
			if (msg=="") {
				if (_ajax_success) {
					_ajax_success(data, status);
				}
			} else {
				_new_ajax_error(XHR, msg, status);
			}
		}
	};
	
	$.ajax=function(options) {
		//var _data = $.extend(data,{token : $(document).data("tokenName"), seedName : $(document).data("seedName")});  
		var sessionToken=$(document).data("token");
		var seed=Math.round(Math.random()*1000000000000);
		var data={};
		var dataType=typeof(options.data);
		
		data[$(document).data("tokenName")]=$.validate($(document).data("token"), seed, options.url);
		data[$(document).data("seedName")]=seed;
		var _callback=function(result) {
		  callback(result);
		}
		if (options.url.indexOf("/_resources/")==-1) {
			if (dataType=="object") {
			 $.extend(options.data, data);
			} else if (dataType=="string") {
			  options.data=options.data+"&"+$.param(data);
			} else {
			  options.data=data;
			}
		}
		_ajax_error=options.error;
		_ajax_success=options.success;
		options.error=_new_ajax_error;
		options.success=_new_ajax_success;
		return _ajax(options);
	};
	
	$.location=function(url, method) {
		var seed=Math.round(Math.random()*1000000000000);
		var token=$.validate($(document).data("token"), seed, url);
		if (method==null || method.toUpperCase()=="GET") {
			location=url+(url.indexOf("?")!=-1 ? "&" : "?")+$(document).data("seedName")+"="+seed+"&"+$(document).data("tokenName")+"="+token;
		} else {
			var form=$(document.body).find(">form#location_request");
			if (form.size()<1) {
				form=$('<form id="location_request" method="post"><input type="hidden" id="seed" name="'+$(document).data("seedName")+'" value=""><input type="hidden" id="token" name="'+$(document).data("tokenName")+'" value=""></form>');
				$(document.body).append(form);
			}
			form.attr("action", url);
			form.find(">input#seed").val(seed);
			form.find(">input#token").val(token);
			form.submit();
		}
	};
	
  
})(jQuery);