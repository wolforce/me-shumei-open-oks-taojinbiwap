package me.shumei.open.oks.taojinbiwap;

import java.io.IOException;
import java.util.HashMap;

import org.json.JSONObject;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.content.Context;

/**
 * 使签到类继承CommonData，以方便使用一些公共配置信息
 * @author wolforce
 *
 */
public class Signin extends CommonData {
	String resultFlag = "false";
	String resultStr = "未知错误！";
	
	String user;
	String pwd;
	
	String loginUrl = "http://login.m.taobao.com/login.htm";
	String loginSubmitUrl;
	String coinSubmitUrl = "http://i.m.taobao.com/t.do?cmd=takeCoin";//领取淘金币的页面
	String captchaUrl;//验证码地址
	
	/**
	 * <p><b>程序的签到入口</b></p>
	 * <p>在签到时，此函数会被《一键签到》调用，调用结束后本函数须返回长度为2的一维String数组。程序根据此数组来判断签到是否成功</p>
	 * @param ctx 主程序执行签到的Service的Context，可以用此Context来发送广播
	 * @param isAutoSign 当前程序是否处于定时自动签到状态<br />true代表处于定时自动签到，false代表手动打开软件签到<br />一般在定时自动签到状态时，遇到验证码需要自动跳过
	 * @param cfg “配置”栏内输入的数据
	 * @param user 用户名
	 * @param pwd 解密后的明文密码
	 * @return 长度为2的一维String数组<br />String[0]的取值范围限定为两个："true"和"false"，前者表示签到成功，后者表示签到失败<br />String[1]表示返回的成功或出错信息
	 */
	public String[] start(Context ctx, boolean isAutoSign, String cfg, String user, String pwd) {
		//把主程序的Context传送给验证码操作类，此语句在显示验证码前必须至少调用一次
		CaptchaUtil.context = ctx;
		//标识当前的程序是否处于自动签到状态，只有执行此操作才能在定时自动签到时跳过验证码
		CaptchaUtil.isAutoSign = isAutoSign;
		
		this.user = user;
		this.pwd = pwd;
		
		try{
			//存放Cookies的HashMap
			HashMap<String, String> cookies = new HashMap<String, String>();
			//Jsoup的Response
			Response res;
			//Jsoup的Document
			Document doc;
			
			//需要提交的数据
			HashMap<String, String> postDatas = new HashMap<String, String>();
			
			//访问登录页面
			res = Jsoup.connect(loginUrl).cookies(cookies).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).referrer(loginUrl).method(Method.GET).execute();
			cookies.putAll(res.cookies());
			//获取登录需要提交的信息
			postDatas = getLoginDatas(res.parse(), cookies);
			//提交登录信息
			doc = loginTaoBao(cookies, postDatas);
			if(doc.html().contains("登录成功"))
			{
				//没有验证码，那就直接签到
				submitCoin(cookies);
			}
			else if(doc.html().contains("注：验证码不区分大小写，点击图片可更换验证码"))
			{
				//在登录成功后碰到验证码
				//获取二次验证登录信息
				postDatas = getAuthCodeLoginDatas(doc, cookies);
				if(CaptchaUtil.showCaptcha(captchaUrl, UA_ANDROID, cookies, "淘金币", user, "登录成功后需要验证码验证"))
				{
					if (CaptchaUtil.captcha_input.length() > 0)
					{
						postDatas.put("TPL_checkcode", CaptchaUtil.captcha_input);
						doc = loginTaoBao(cookies, postDatas);
						//领取淘金币
						submitCoin(cookies);
					}
					else
					{
						this.resultFlag = "false";
						this.resultStr = "用户取消输入验证码";
						return new String[]{resultFlag, resultStr};
					}
				}
				else
				{
					this.resultFlag = "false";
					this.resultStr = "拉取验证码失败，无法登录";
					return new String[]{resultFlag, resultStr};
				}
			}
			else if(doc.html().contains("验证码") && doc.html().contains("免登录"))
			{
				//在登录面板碰到验证码
				//重新获取登录需要提交的信息
				postDatas = getLoginDatas(doc, cookies);
				//拉取显示验证码并让用户填写
				if(CaptchaUtil.showCaptcha(captchaUrl, UA_ANDROID, cookies, "淘金币", user , "登录时需要验证码"))
				{
					//获取验证码成功，那就把用户输入的验证码写入HashMap
					postDatas.put("TPL_checkcode", CaptchaUtil.captcha_input);
					//提交带有验证码的登录信息
					doc = loginTaoBao(cookies, postDatas);
					submitCoin(cookies);//领取淘金币
				}
				else
				{
					this.resultFlag = "false";
					this.resultStr = "拉取验证码失败，无法登录";
					return new String[]{resultFlag, resultStr};
				}
			}
			else if(doc.html().contains("网页登录保护"))
			{
				this.resultFlag = "false";
				this.resultStr = "账号设置了网页登录保护，无法签到。如需签到，请取消网页登录保护";
			}
			else if(doc.html().contains("暂停支持邮箱登录"))
			{
				this.resultFlag = "false";
				this.resultStr = "暂停支持邮箱登录，请使用会员名或者手机号进行登录。";
			}
			else if(doc.html().contains("密码和账户名不匹配"))
			{
				this.resultFlag = "false";
				this.resultStr = "密码和账户名不匹配";
			}
			else
			{
				this.resultFlag = "false";
				this.resultStr = "登录失败，请检查账号密码是否正确，网络连接是否正常。";
			}

			
		} catch (IOException e) {
			this.resultFlag = "false";
			this.resultStr = "连接超时";
			e.printStackTrace();
		} catch (Exception e) {
			this.resultFlag = "false";
			this.resultStr = "未知错误！";
			e.printStackTrace();
		}
		
		return new String[]{resultFlag, resultStr};
	}
	
	
	
	/**
	 * 访问登录页面，并获取需要提交的信息
	 * @param doc
	 * @param cookies
	 * @return 需要Post的HashMap数据
	 * @throws IOException
	 */
	public HashMap<String, String> getLoginDatas(Document doc, HashMap<String, String> cookies) throws IOException
	{
		loginSubmitUrl = doc.getElementById("J_Login").attr("action");
		String _tb_token_ = doc.getElementsByAttributeValue("name", "_tb_token_").first().val();
		String sid = doc.getElementsByAttributeValue("name", "sid").first().val();
		String _umid_token = doc.getElementsByAttributeValue("name", "_umid_token").first().val();

		HashMap<String, String> postFormDatas = new HashMap<String, String>();//需要POST的数据
		postFormDatas.put("TPL_username", user);
		postFormDatas.put("TPL_password", pwd);
		postFormDatas.put("_tb_token_", _tb_token_);
		postFormDatas.put("action", "IphoneLoginAction");
		postFormDatas.put("event_submit_do_login", "1");
		postFormDatas.put("TPL_redirect_url", "");
		postFormDatas.put("sid", sid);
		postFormDatas.put("_umid_token", _umid_token);
		
		//碰到验证码时，需要额外增加一些验证码信息
		if(doc.html().contains("验证码"))
		{
			captchaUrl = doc.getElementById("J_StandardCode").attr("src");
			String wapCheckId = doc.getElementsByAttributeValue("name", "wapCheckId").first().val();
			postFormDatas.put("wapCheckId", wapCheckId);
			postFormDatas.put("need_check_code", "yes");
		}
		
		return postFormDatas;
	}
	
	
	/**
	 * 获取登录成功后出现二次验证码验证的账号登录信息
	 * @param doc
	 * @param cookies
	 * @return 需要Post的HashMap数据
	 * @throws IOException
	 */
	public HashMap<String, String> getAuthCodeLoginDatas(Document doc, HashMap<String, String> cookies) throws IOException
	{
		captchaUrl = doc.getElementById("J_StandardCode").attr("src");
		loginSubmitUrl = doc.getElementsByTag("form").first().attr("action");
		String sid = doc.getElementsByAttributeValue("name", "sid").first().val();
		String TPL_redirect_url = doc.getElementsByAttributeValue("name", "TPL_redirect_url").first().val();
		String token = doc.getElementsByAttributeValue("name", "token").first().val();
		String wapCheckId = doc.getElementsByAttributeValue("name", "wapCheckId").first().val();
		
		HashMap<String, String> postFormDatas = new HashMap<String, String>();//需要POST的数据
		postFormDatas.put("sid", sid);
		postFormDatas.put("ssottid", "");
		postFormDatas.put("TPL_username", "IphoneLoginAction");
		postFormDatas.put("TPL_redirect_url", TPL_redirect_url);
		postFormDatas.put("token", token);
		postFormDatas.put("wapCheckId", wapCheckId);
		postFormDatas.put("action", "IphoneLoginAction");
		postFormDatas.put("event_submit_do_check_code", "1");
		
		return postFormDatas;
	}
	
	
	/**
	 * 登录淘宝账号
	 * @param loginSubmitUrl
	 * @param cookies
	 * @param postDatas
	 * @return resultString 结果页面的Jsoup Document对象
	 * @throws IOException
	 */
	public Document loginTaoBao(HashMap<String, String> cookies, HashMap<String, String> postDatas) throws IOException
	{
		//提交登录信息
		Response res;
		res = Jsoup.connect(loginSubmitUrl).data(postDatas).cookies(cookies).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).referrer(loginSubmitUrl).method(Method.POST).execute();
		cookies.putAll(res.cookies());
		return res.parse();
	}
	
	
	/**
	 * 提交领取淘金币的命令
	 * @param cookies
	 * @throws IOException
	 */
	public void submitCoin(HashMap<String, String> cookies) throws IOException
	{
		Response res;
		
		for(int i=0;i<1;i++)
		{
			try {
				//访问淘金币页面
				//{"result":true,"loginIn":true,"checkCode":false,"state":1,"total":19820,"today":5,"tomorrow":10,"times":1} //已经签到
				//{"result":true,"loginIn":true,"checkCode":false,"state":0,"total":19830,"today":10,"tomorrow":15,"times":2}
				//{"result":true,"loginIn":true,"checkCode":true,"codeUrl":"http://checkcode.taobao.com/auction/checkcode?sessionID=088f59e75b4f11ed8195eca82b76b303&seed=1369671612366","state":0,"total":0,"today":0,"tomorrow":0,"times":0}
				res = Jsoup.connect(coinSubmitUrl).cookies(cookies).userAgent(UA_CHROME).timeout(TIME_OUT).ignoreContentType(true).referrer(loginUrl).method(Method.GET).execute();
				cookies.putAll(res.cookies());
				JSONObject jsonObj = new JSONObject(res.body());
				if (jsonObj.getBoolean("loginIn") == false) {
					this.resultFlag = "false";
					this.resultStr = "登录出错，有可能是账号密码不正确，或者账号设置了登录保护";
				} else {
					String submitSigninResultStr = res.body();
					boolean checkCode = jsonObj.getBoolean("checkCode");
					if (checkCode) {
						String codeUrl = jsonObj.getString("codeUrl");
						if (CaptchaUtil.showCaptcha(codeUrl, UA_ANDROID, cookies, "淘金币", user, "领取淘金币时需要验证码")) {
							if(CaptchaUtil.captcha_input.length() > 0) {
								//获取验证码成功，可以用CaptchaUtil.captcha_input继续做其他事了
								res = Jsoup.connect(coinSubmitUrl + "&checkCode=" + CaptchaUtil.captcha_input).cookies(cookies).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).referrer(loginUrl).method(Method.GET).execute();
								submitSigninResultStr = res.body();
							} else {
								//用户取消输入验证码
								this.resultFlag = "false";
								this.resultStr = "用户取消输入验证码";
							}
						} else {
							//拉取验证码失败，签到失败
							this.resultFlag = "false";
							this.resultStr = "领取淘金币时遇到验证码，拉取验证码失败";
						}
					}
					analyseSignedCoinInfo(submitSigninResultStr);
				}
			} catch (Exception e) {
				e.printStackTrace();
				this.resultFlag = "false";
				this.resultStr = "登录成功，但提交签到请求时发生未知错误";
			}
		}
	}
	
	
	/**
	 * 分析签到得到的淘金币信息
	 * @param jsonStr
	 * @return
	 */
	private void analyseSignedCoinInfo(String jsonStr)
	{
		try {
			JSONObject jsonObj = new JSONObject(jsonStr);
			boolean checkCode = jsonObj.getBoolean("checkCode");
			if (checkCode) {
				this.resultFlag = "false";
				this.resultStr = "输入领取淘金时弹出的验证码错误，领取失败";
			} else {
				String total = jsonObj.getString("total");
				String today = jsonObj.getString("today");
				int times = jsonObj.getInt("times");
				String tomorrow = jsonObj.getString("tomorrow");
				this.resultFlag = "true";
				this.resultStr = "签到成功，获得" + today + "金币，明天可领" + tomorrow + "金币，现有" + total + "金币，连领" + times + "天";
			}
		} catch (Exception e) {
			this.resultFlag = "false";
			this.resultStr = "登录成功但领取淘金币时出现未知错误";
		}
	}
	
	
}
