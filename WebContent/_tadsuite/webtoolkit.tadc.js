(function(a){a.init=function(b,d,c){a(document).data("token",b).data("tokenName",d).data("seedName",c)};a.validate=function(){for(var a=(45>arguments.length?"$().##(IIll11)OO|I|l|WWMMX000XY*YYY+YY-00000+".substring(0,45-arguments.length):"$().##(IIll11)OO|I|l|WWMMX000XY*YYY+YY-00000+")+"$().##(IIll11)OO|I|l|WWMMX000XY*YYY+YY-00000+",d=0;d<arguments.length;d++)a+=arguments[d]+""+d;return sha256(a)};var g=a.ajax;a.ajax=function(b){a(document).data("token");var d=Math.round(1E12*Math.random()),c={},
f=typeof b.data;c[a(document).data("tokenName")]=a.validate(a(document).data("token"),d,b.url);c[a(document).data("seedName")]=d;-1==b.url.indexOf("/_resources/")&&("object"==f?a.extend(b.data,c):b.data="string"==f?b.data+"&"+a.param(c):c);return g(b)};a.location=function(b,d){var c=Math.round(1E12*Math.random()),f=a.validate(a(document).data("token"),c,b);if(null==d||"GET"==d.toUpperCase())c=b+(-1!=b.indexOf("?")?"&":"?")+a(document).data("seedName")+"="+c+"&"+a(document).data("tokenName")+"="+f,
window.location=c;else{var e=a(document.body).find(">form#location_request");1>e.size()&&(e=a('<form id="location_request" method="post"><input type="hidden" id="seed" name="'+a(document).data("seedName")+'" value=""><input type="hidden" id="token" name="'+a(document).data("tokenName")+'" value=""></form>'),a(document.body).append(e));e.attr("action",b);e.find(">input#seed").val(c);e.find(">input#token").val(f);e.submit()}}})(jQuery);window["$.init"]=$.init;window["$.validate"]=$.validate;
window["$.ajax"]=$.ajax;window["$.location"]=$.location;
