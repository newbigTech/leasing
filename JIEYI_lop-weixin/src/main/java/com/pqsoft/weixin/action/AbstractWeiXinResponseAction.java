package com.pqsoft.weixin.action;

import java.util.Map;
import java.util.UUID;

import org.jfree.util.Log;

import com.pqsoft.skyeye.api.Action;
import com.pqsoft.skyeye.api.Reply;
import com.pqsoft.skyeye.api.SkyEye;
import com.pqsoft.skyeye.dev.aDev;
import com.pqsoft.skyeye.exception.ActionException;
import com.pqsoft.skyeye.rbac.api.aAuth;
import com.pqsoft.skyeye.rbac.api.aAuth.aAuthType;
import com.pqsoft.util.StringUtils;
import com.pqsoft.weixin.utils.AppUtils;

/**
 * 所有类weixin的action都继承这个类
 * 
 * @author LUYANFENG @ 2015年2月28日
 */
public abstract class AbstractWeiXinResponseAction extends Action {

	/**
	 * oracle 汉字拼音排序用的 <br />
	 * 
	 *  如：nlssort(translate(lower(t.catena_name),'abcdefghjklmnopqrstwxyz',#{magic}), 'nls_sort = schinese_pinyin_m') asc
	 */
	protected final String oracle_magic = "啊八嚓大额发噶哈几卡拉吗呐哦扒七然仨他哇西呀杂";
	/**
	 * 微信请求 自动生成当前action路径（要参数吗？）
	 */
	protected final String WX_check_URI =  SkyEye.getRequest().getRequestURL().toString()
			+"?"+SkyEye.getRequest().getQueryString()
			;

	/**
	 * 是多例的话就可以在这里做点事
	 */
	public AbstractWeiXinResponseAction() {
		System.out.println("### 处理 对微信的请求");
		Map<String, Object> _getParameters = _getParameters();
		String fromToken = (String) _getParameters.get(AppUtils.MYTOKEN);
		// 空说明不需要验证
		if(!StringUtils.isBlank(fromToken) && !AppUtils.equalsNowToken( fromToken) ){
			throw new ActionException("请不要试图重复操作！！！");
		}
		AppUtils.generateToken();
		
	}

	@Override
	@aDev(code = "170043", email = "lichaohn@163.com", name = "luyanfeng")
	@aAuth(type = aAuthType.ALL)
	public Reply execute() {
		// TODO 一些坏事
		Log.debug("### 来至微信的请求 execute");
		return null;
	}
	
	

}
