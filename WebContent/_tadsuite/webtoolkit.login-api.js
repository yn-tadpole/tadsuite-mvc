/***
本脚本用于实现TADSUITE登录页的支持。
登录必要的条件：
  1.引入jQuery库
  2.引入webtoolkit.digest.js
  3.引入webtoolkit.tadc.js
  4.引入webtoolkit.login-api.js（即本文件）
  5.调用JS初始化：
    $.init("${csrfTokenMark}", "${csrfTokenName}", "${csrfTokenSeedName}");
    LOGIN_API.init("${auth_type!}", "${auth_path!}", "${auth_swapKey!}", "${auth_passwordEncrypt!}", "${auth_useValidationCode?string('Y', 'N')}");
  6.包含登录表单及必要的元素
    <form action="" method="post" onsubmit="return LOGIN_API.submit(this);" id="login-form">
      <input type="text" id="username" >
      <input type="password" id="password" >
      <div class="vilidate-code-area"><!--用于显示验证码-->
        <input type="text" id="valcode" onKeyUp="LOGIN_API.checkValCode(this);">
        <img id="validationImage" src="${auth_path}?auth_action=getval&w=150&h=45&r=255&g=255&b=255" onclick="LOGIN_API.refreshValCode(this);" />
      </div>
      <div class="login-message"></div> <!--用于显示登录提示信息-->
    </div>
*/
var LOGIN_API=new function() {
    var api=this;
    var _authType, _authPath, _swapKey, _passwordEncrypt, _useValidationCode;
    var _crossDomain=false, _valCodeValid=-1, _submiting=false, _validating=false;
    var $container;
		
    api.init=function(authType, authPath, swapKey, passwordEncrypt, useValidationCode) {
        _authType=authType;
        _authPath=authPath;
        _swapKey=swapKey;
        _passwordEncrypt=passwordEncrypt;
        _useValidationCode=useValidationCode=="Y" || useValidationCode=="1" || useValidationCode=="true";
        $container=$("#login-form");
				if (_useValidationCode) {
					$container.find(".vilidate-code-area").show();
				} else {
					$container.find(".vilidate-code-area").hide();
				}
        _crossDomain=_authPath.toLowerCase().indexOf("http://")!=-1 || _authPath.toLowerCase().indexOf("https://")!=-1;
        if (_authType=="sso") {
          if (_crossDomain) {
            api.getScript(_authPath+"?auth_action=check&swap_key="+_swapKey);
          } else {
            $.get(_authPath+"?auth_action=check&swap_key="+_swapKey, function(data) { eval(data)});
          }
        }
        $container.find("#username").focus();
    }
		
		api.refreshValCode=function(img) {
			var char=img.src.indexOf("?")!=-1 ? "&" : "?";
			var x=img.src.indexOf("mark=");
			img.src=(x!=-1 ? img.src.substring(0, x) : img.src+char)+"mark="+(Math.random()*10000000)
		}
    
    api.checkValCode=function(obj) {
       if (_submiting || _validating) {
            return;
       }
       if (obj.value.length<4) {
            $(obj).css("color", "black");
            _valCodeValid=-1;
            return;
        }
			 _validating=true;
       $.post(_authPath+"?auth_action=checkval", {fdValidationStr : obj.value}
            , function(data, status) {
										if (data=="1") {
												$(obj).css("color", "#090");
												$container.find(".login-message").html("");
												_valCodeValid=1;
										} else if (data=="0") {
												$(obj).css("color", "red");
												$container.find(".login-message").html("验证码不正确！").css("color", "red");
												_valCodeValid=0;
										} else {
											var url=location.toString();
											var x=url.indexOf("#");
											location=x!=-1 ? url.substring(0, x) : url;
										}
								_validating=false;
             });
    }
    
    api.submit=function(form) {
        if (_submiting || _validating) {
            return false;
        }
        var username=$(form).find("#username").val();
        var password=$(form).find("#password").val();
        var valcode="";
        var saveState=$(form).find("#save-state").prop("checked") ? "1" : "0"; //notice: don't use attr('checked')
        if (username.length<1) {
            $container.find(".login-message").html("请填写用户名").css("color", "red");
            return false;
        }
        if (password.length<1) {
            $container.find(".login-message").html("请填写密码").css("color", "red");
            return false;
        }
        if (_useValidationCode) {
           valcode= $(form).find("#valcode").val();
            if (valcode.length<1) {
                $container.find(".login-message").html("请填写验证码").css("color", "red");
                return false;
            } else if (_valCodeValid==0) {//因为手机端有时不会触发前置验证，这是仅在已经触发且不通过时才阻断。
                $container.find(".login-message").html("验证码不正确").css("color", "red");
                return false;
            }
        }
        _submiting=true;
        $container.find(".login-message").html("正在提交，请稍候……").css("color", "white");
        var encryptPassword=sha256((_passwordEncrypt=="MD5" ? md5(password) : _passwordEncrypt=="SHA256" ? sha256(password) : _passwordEncrypt=="COMBINE" ? sha256(sha256(username)+password) : password)+_swapKey);
        if (_authType=="sso") {// && _crossDomain
          var params="&fdUsername="+username+"&fdPassword="+encryptPassword+"&fdValidationStr="+valcode+"&fdSaveState="+saveState;
          api.getScript(_authPath+"?auth_action=login&callback=script&swap_key="+_swapKey+params);
          _submiting=false;
        } else {
          $.post(_authPath+"?auth_action=login", {fdUsername : username, fdPassword : encryptPassword, fdValidationStr : valcode, fdSaveState : saveState, swap_key : _swapKey}, function (data, status) {
              data=data.toString();
              eval(data);
              _submiting=false;
          });
        }
        return false;
    }
    
    api.logout=function(redirectURL) {
				var url=location.toString();
				var x=url.indexOf("#");
				url=x!=-1 ? url.substring(0, x) : url;
				$.location(location.pathname+"?auth_action=logout&url="+encodeURI(redirectURL!=null ? redirectURL : url), "POST");
        /*$.post("?auth_action=logout", {}, function (data, status) {
            location=redirectURL;
        });*/
    }
    
    api.getScript=function (url) {
      var seed=Math.round(Math.random()*1000000000000);
      var token=$.validate($(document).data("token"), seed, url);
      var oHead = document.getElementsByTagName('HEAD').item(0);
      var oScript= document.createElement("script");
      oScript.type = "text/javascript";
      oScript.src=url+(url.indexOf("?")!=-1 ? "&" : "?")+$(document).data("seedName")+"="+seed+"&"+$(document).data("tokenName")+"="+token;
      oHead.appendChild(oScript); 
    }
    
    api.showFinished=function() {
      $container.find(".login-message").html("登录成功，等待刷新页面……").css("color", "");
      var url=location.toString();
      var x=url.indexOf("#");
      location=x!=-1 ? url.substring(0, x) : url;
    }
    
    api.showError=function(message) {
      if (message.indexOf("验证码")!=-1 && !_useValidationCode) {
        document.cookie = "Auth_useValidationCode=Y";
        var url=location.toString();
        var x=url.indexOf("#");
        location=x!=-1 ? url.substring(0, x) : url;
      }
      $container.find(".login-message").html(message).css("color", "red");
      $container.find("#validationImage").click();
      $container.find("#valcode").val("");
    }
    
    api.showChangePsDialog=function () {
      api.win(_authPath.substring(0, _authPath.lastIndexOf("/")+1)+"ChangePassword?username="+$(form).find("#username").val(), 370, 288);
    }
    
    api.win=function(url,width,height,scrollbar,wtop,wleft,wname) {	
      if (width==null || width=="") width=screen.availWidth-100;
      if (height==null || height=="") height=screen.availHeight-100;
      if (scrollbar!="yes") scrollbar="no";
      if (wname==null) wname="";
      if (wleft==null) wleft=(screen.availWidth-width)/2;
      if (wtop==null) wtop=(screen.availHeight-height-30)/2;
      if (navigator.userAgent.toString().indexOf("MSIE 6")!=-1) {
        width+=6;
        height+=40;
      }
      if (!window.open(url,wname,'width='+width+',height='+height+',top='+wtop+',left='+wleft+',scrollbars='+scrollbar+',status=no,resizable=yes,toolbar=no,menubar=no,location=no')) {
        alert("窗口弹出失败，如果你正在使用某些拦截弹出窗口的软件，请关闭它们。");
      }
      //window不能返回一个值，否则类似<a href=Javascript:win的链接会转到一个空白页面。
    }
   
    api.serverCallback=function(code, message) {
      switch (code) {
        case "note_info" : 
          $container.find(".login-message").html(message).css("color", "");
          $container.find("#username").focus();
          return;
        case "swap_pass" :
        case "success" :
          api.showFinished();
          return;
        case "error" :
          if (message=="validate") {
            location=location;
            return;
          }
          api.showError(message);
          return;
        case "changeps" :
          api.showError("密码已经过期，必须修改密码后才能登录。<a href=\"javascript:void LOGIN_API.showChangePsDialog();\">点击修改</a>");
          return;
        default : 
          alert(code+","+message);
          return;
      }
   }
}
