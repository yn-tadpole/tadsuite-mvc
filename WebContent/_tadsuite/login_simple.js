var LOGIN_API=new function() {
    var api=this;
    var _swapKey, _passwordEncrypt, _useValidationCode;
    var _valCodeValid=-1, _submiting=false, _validating=false;
		
    api.init=function(swapKey, passwordEncrypt, useValidationCode) {
        _swapKey=swapKey;
        _passwordEncrypt=passwordEncrypt;
        _useValidationCode=useValidationCode=="Y" || useValidationCode=="1" || useValidationCode=="true";
				if (_useValidationCode) {
					$(".login-content .vilidate-code-area").show();
				} else {
					$(".login-content .vilidate-code-area").hide();
				}
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
       $.post(location.pathname+"?auth_action=checkval", {fdValidationStr : obj.value}
            , function(data, status) {
										if (data=="1") {
												$(obj).css("color", "#090");
												$(".login-content .message").html("");
												_valCodeValid=1;
										} else if (data=="0") {
												$(obj).css("color", "red");
												$(".login-content .message").html("验证码不正确！").css("color", "red");
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
        if (username.length<1) {
            $(".login-content .message").html("请填写用户名").css("color", "red");
            return false;
        }
        if (password.length<1) {
            $(".login-content .message").html("请填写密码").css("color", "red");
            return false;
        }
        if (_useValidationCode) {
           valcode= $(form).find("#valcode").val();
            if (valcode.length<1) {
                $(".login-content .message").html("请填写验证码").css("color", "red");
                return false;
            } else if (_valCodeValid==0) {//因为手机端有时不会触发前置验证，这是仅在已经触发且不通过时才阻断。
                $(".login-content .message").html("验证码不正确").css("color", "red");
                return false;
            }
        }
        _submiting=true;
        $(".login-content .message").html("正在提交，请稍候……").css("color", "white");
        var encryptPassword=sha256((_passwordEncrypt=="MD5" ? md5(password) : _passwordEncrypt=="SHA256" ? sha256(password) : _passwordEncrypt=="COMBINE" ? sha256(sha256(username)+password) : password)+_swapKey);
        $.post(location.pathname+"?auth_action=login", {fdUsername : username, fdPassword : encryptPassword, fdValidationStr : valcode}, function (data, status) {
            data=data.toString();
            if (data=="success" || data=="validate") {
                var url=location.toString();
                var x=url.indexOf("#");
                location=x!=-1 ? url.substring(0, x) : url;
            } else {
                 $(".login-content #validationImage").click();
                 $(".login-content #valcode").val("");
                 $(".login-content .message").html(data).css("color", "red");
            }
            _submiting=false;
        });
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
}
