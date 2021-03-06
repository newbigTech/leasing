package com.pqsoft.overdue.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONArray;

import org.apache.log4j.Logger;

import com.pqsoft.base.task.service.TaskClaimService;
import com.pqsoft.dataDictionary.service.DataDictionaryMemcached;
import com.pqsoft.entity.Excel;
import com.pqsoft.skyeye.api.Dao;
import com.pqsoft.skyeye.api.Page;
import com.pqsoft.skyeye.exception.ActionException;
import com.pqsoft.skyeye.math.Finance;
import com.pqsoft.skyeye.util.UTIL;
import com.pqsoft.util.BaseUtil;
import com.pqsoft.util.PageUtil;

public class OverdueService {
	private final Logger logger = Logger.getLogger(getClass());

	/**
	 * 默认单利计算罚息
	 *
	 * @return
	 */
	public boolean isSimple() {
		List<Map<String, Object>> list = new DataDictionaryMemcached()
				.get("罚息");
		for (Map<String, Object> map : list) {
			if ("罚息计算模式".equals(map.get("FLAG").toString().trim())) {
				if ("单利".equals(map.get("CODE").toString().trim())) {
					logger.debug(map.get("CODE").toString().trim());
					return true;
				}
				if ("复利".equals(map.get("CODE").toString().trim())) {
					logger.debug(map.get("CODE").toString().trim());
					return false;
				}
			}
		}
		logger.debug("未读入数据字典配置：默认单利");
		return true;
	}

	/**
	 * 默认单利计算罚息
	 *
	 * @return
	 */
	public List<Map<String, Object>> getRate() {
		List<Map<String, Object>> re = new ArrayList<Map<String, Object>>();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> list = new DataDictionaryMemcached().get("罚息");
		for (Map<String, Object> map : list) {
			if ("罚息率".equals(map.get("FLAG").toString().trim())) {
				// "10,0.5;max,0.1"
				// 多罚息率封装，天数需要依次累加
				int d = 0;
				String str = map.get("CODE").toString().trim();
				for (String s1 : str.split("[;；]")) {
					String[] s2 = s1.split("[,，]");
					if ("max".equals(s2[0].trim()) && d == -1)
						continue;
					if (!"max".equals(s2[0].trim())) {
						int dd = Integer.parseInt(s2[0].trim());
						if (dd <= d)
							continue;
						d = dd;
					}
					Map<String, Object> m = new HashMap<String, Object>();
					m.put("d", s2[0]);
					m.put("r", s2[1]);
					if ("max".equals(s2[0].trim())) {
						d = -1;
					} else {
						d = Integer.parseInt(s2[0].trim());
					}
					re.add(m);
				}
				return re;
			}
		}
		if (re.size() == 0) {
			Map<String, Object> m = new HashMap<String, Object>();
			m.put("d", "max");
			m.put("r", "0.0005");
			re.add(m);
		}
		logger.debug("罚息率:" + re);
		return re;
	}

	public static Map<String, Object> _m = new HashMap<String, Object>();
	static {
		_m.put("TEXT_D", "第");
		_m.put("TEXT_QWYJ", "期违约金");
		_m.put("TEXT_WYJ", "违约金");
	}

	public void upDueOne(String payCode) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("PAY_CODE", payCode);
		Dao.selectOne("upDueOne", map);
	}

	// public void upDueAll() {
	// {// 租金未付清
	// List<String> pays = Dao.selectList("fi.overdue.getOverduePays", new
	// HashMap<String, Object>(_m));
	// boolean simple = isSimple();
	// List<Map<String, Object>> rates = getRate();
	// for (String payCode : pays) {
	// try {
	// upDue(payCode, simple, rates);
	// Dao.commit();
	// } catch (Exception e) {
	// logger.error(e, e);
	// Dao.rollback();
	// } finally {
	// Dao.close();
	// }
	// }
	// }
	// {// 租金付清了，但罚息未付清
	// List<Map<String, Object>> list = Dao.selectList("fi.overdue.getOveItem2",
	// new HashMap<String, Object>(_m));
	// for (Map<String, Object> map : list) {
	// while (true) {
	// Map<String, Object> due = Dao.selectOne("fi.overdue.getDueMax", map);
	// if (due == null) break;
	// if ("isToday".equals(due.get("ISTODAY"))) break;
	// due.put("PENALTY_DAY", ((BigDecimal) map.get("PENALTY_DAY")).intValue() +
	// 1);
	// Calendar calendar = Calendar.getInstance();
	// calendar.setTime((Date) due.get("CREATE_DATE"));
	// calendar.add(Calendar.DATE, 1);
	// due.put("CREATE_DATE", calendar.getTime());
	// Dao.insert("fi.overdue.addOverdue", due);
	// }
	// }
	// }
	// }

	/**
	 * 预估某个支付表未来罚息
	 *
	 * @param PAYLIST_CODE
	 *            支付表编号
	 * @param PAY_DATE
	 *            未来罚息日期
	 */
	public double dunPayList(String PAYLIST_CODE,String PAY_DATE){
		double dunMoney = 0d;

		boolean simple = isSimple();
		List<Map<String, Object>> rates = getRate();
		double rate = 0;
		for (Map<String, Object> rateMap : rates) {
			if ("max".equals(rateMap.get("d"))) {
				rate = Double.parseDouble((String) rateMap.get("r"));
				break;
			}
		}

		//分两种数据
		//1.在运行此方法已经逾期
		//2.在运行此方法未逾期但是未来罚息日期已经逾期
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("PAYLIST_CODE", PAYLIST_CODE);
		map.put("PAY_DATE", PAY_DATE);
		List dunList = Dao.selectList("fi.overdue.queryDunListByPayListCode",map);
		for (Object aDunList : dunList) {
			Map dunMap = (Map) aDunList;
			//未还租金
			double beginning_money = (dunMap.get("BEGINNING_MONEY") != null && !dunMap.get("BEGINNING_MONEY").equals("")) ? Double.parseDouble(dunMap.get("BEGINNING_MONEY").toString()) : 0d;
			//未还罚息
			double PENALTY_RECE = (dunMap.get("PENALTY_RECE") != null && !dunMap.get("PENALTY_RECE").equals("")) ? Double.parseDouble(dunMap.get("PENALTY_RECE").toString()) : 0d;
			//预计天数
			int dun_day = (dunMap.get("DUN_DAY") != null && !dunMap.get("DUN_DAY").equals("")) ? Integer.parseInt(dunMap.get("DUN_DAY").toString()) : 0;
//			System.out.println("---------beginning_money=" + beginning_money + "-------PENALTY_RECE=" + PENALTY_RECE + "--------dun_day=" + dun_day + "-------rate=" + rate);
			if (simple) {
				dunMoney = dunMoney + Finance.cfx(beginning_money, PENALTY_RECE, dun_day, rate);
			} else {
				dunMoney = dunMoney + Finance.sfx(beginning_money, PENALTY_RECE, dun_day, rate);
			}
		}
		return dunMoney;
	}
	/**
	 * 预估未来罚息
	 *
	 * @param m
	 *            未还租金
	 * @param f
	 *            未还罚息
	 * @param d
	 *            预计天数
	 * @return
	 */
	public double dueTemp(double m, double f, int d) {
		boolean simple = isSimple();
		List<Map<String, Object>> rates = getRate();
		double rate = 0;
		for (Map<String, Object> rateMap : rates) {
			if ("max".equals(rateMap.get("d"))) {
				rate = Double.parseDouble((String) rateMap.get("r"));
				break;
			}
			// if (dt <= Integer.parseInt((String) rateMap.get("d"))) {
			// rate = Double.parseDouble((String) rateMap.get("r"));
			// break;
			// }
		}
		if (simple) {
			return Finance.cfx(m, f, d, rate);
		} else {
			return Finance.sfx(m, f, d, rate);
		}
	}

	/**
	 * 预估某个支付表未来应付金额
	 *
	 * @param PAYLIST_CODE
	 *            支付表编号
	 * @param PAY_DATE
	 *            未来日期
	 */
	public double zjPayList(String PAYLIST_CODE,String PAY_DATE){
		double zujinMoney=0d;

		Map<String, Object> map=new HashMap<String, Object>();
		map.put("PAYLIST_CODE", PAYLIST_CODE);
		map.put("PAY_DATE", PAY_DATE);
		Map mapdata=Dao.selectOne("fi.overdue.queryZujinByData",map);
		if(mapdata !=null && mapdata.get("BEGINNING_MONEY") !=null && !mapdata.get("BEGINNING_MONEY").equals("")){
			zujinMoney=Double.parseDouble(mapdata.get("BEGINNING_MONEY").toString());
		}
		return zujinMoney;
	}

	/**
	 * 预估某个支付表未来逾期天数
	 *
	 * @param PAYLIST_CODE
	 *            支付表编号
	 * @param PAY_DATE
	 *            未来日期
	 */
	public double dayPayList(String PAYLIST_CODE,String PAY_DATE){
		int day=0;

		Map<String, Object> map=new HashMap<String, Object>();
		map.put("PAYLIST_CODE", PAYLIST_CODE);
		map.put("PAY_DATE", PAY_DATE);
		Map mapdata=Dao.selectOne("fi.overdue.queryDunDayByData",map);
		if(mapdata !=null && mapdata.get("PENALTY_DAY") !=null && !mapdata.get("PENALTY_DAY").equals("")){
			day=Integer.parseInt(mapdata.get("PENALTY_DAY").toString());
		}

		int dayWl=0;
		Map mapdata1=Dao.selectOne("fi.overdue.queryDayWL",map);
		if(mapdata1 !=null && mapdata1.get("DUN_DAY_WL") !=null && !mapdata1.get("DUN_DAY_WL").equals("")){
			dayWl=Integer.parseInt(mapdata1.get("DUN_DAY_WL").toString());
		}

		return day+dayWl;
	}

//	/**
//	 * 罚息计算（单张支付表）
//	 *
//	 * @param payCode
//	 * @param simple
//	 * @param rates
//	 */
	// public void upDue(String payCode, boolean simple, List<Map<String,
	// Object>> rates) {
	// Map<String, Object> mPay = new HashMap<String, Object>(_m);
	// mPay.put("PAYLIST_CODE", payCode);
	// // ******山重罚息率获取**参考老系统
	// double rate = Dao.selectDouble("fi.overdue.getDunRate", mPay);
	//
	// // 当前时间点相关支付表的对应项目的应收合计，实收，实收罚息
	// List<Map<String, Object>> items =
	// Dao.selectList("fi.overdue.getOverduePayitem", mPay);
	// for (Map<String, Object> item : items) {
	// item.put("PAY_CODE", item.get("PAYLIST_CODE"));
	// item.put("PERIOD", item.get("BEGINNING_NUM"));
	// // 未缴租金
	// double m = Double.parseDouble(item.get("BEGINNING_MONEY").toString()) -
	// Double.parseDouble(item.get("BEGINNING_PAID").toString());
	// while (true) {
	// // 最后一条记录
	// Map<String, Object> due = Dao.selectOne("fi.overdue.getDueMax", item);
	// if (due == null) {
	// if (m < 0.1) break;
	// due = new HashMap<String, Object>();
	// due.put("PAY_CODE", payCode);
	// due.put("PENALTY_RECE", new BigDecimal(0));// 应收罚息
	// due.put("PERIOD", item.get("BEGINNING_NUM"));// 期次
	// due.put("RENT_RECE", item.get("BEGINNING_MONEY"));// 应收租金
	// due.put("PENALTY_DAY", 1);// 应收租金
	// due.put("DUE_STATUS", "0");
	// Calendar calendar = Calendar.getInstance();
	// calendar.setTime((Date) item.get("BEGINNING_RECEIVE_DATA"));
	// calendar.add(Calendar.DATE, 1);
	// due.put("CREATE_DATE", calendar.getTime());
	// } else {
	// if ("isToday".equals(due.get("ISTODAY"))) break;
	// Calendar calendar = Calendar.getInstance();
	// calendar.setTime((Date) due.get("CREATE_DATE"));
	// calendar.add(Calendar.DATE, 1);
	// due.put("CREATE_DATE", calendar.getTime());
	// due.put("PENALTY_DAY", ((BigDecimal) due.get("PENALTY_DAY")).intValue() +
	// 1);// 应收租金
	// }
	// due.put("RENT_DATE", item.get("BEGINNING_RECEIVE_DATA"));// 应收租金
	// due.put("PENALTY_PAID", item.get("D_RECEIVE_MONEY"));// 实收罚息
	// due.put("RENT_PAID", item.get("BEGINNING_PAID"));// 实收租金
	// due.put("PROJECT_ID", item.get("PROJECT_ID"));// 项目ID
	// due.put("CUST_NAME", item.get("CUST_NAME"));
	// due.put("SUP", item.get("SUP"));
	// due.put("EQUI", item.get("EQUI"));
	// due.put("PAY_START", item.get("PAY_START"));
	// // 用于计算罚息的初始金额最后一条罚息记录应缴罚息合计-已收所有罚息合计
	// double f = ((BigDecimal) due.get("PENALTY_RECE")).doubleValue() -
	// ((BigDecimal) item.get("D_RECEIVE_MONEY")).doubleValue();
	//
	// int d = 1;// 天数，每天计算，默认为1
	// // int dt = (Integer) due.get("PENALTY_DAY");// 占用天数
	// // double rate = 0;
	// double xyjfx;
	// // for (Map<String, Object> rateMap : rates) {
	// // if ("max".equals(rateMap.get("d"))) {
	// // rate = Double.parseDouble((String) rateMap.get("r"));
	// // break;
	// // }
	// // if (dt <= Integer.parseInt((String) rateMap.get("d"))) {
	// // rate = Double.parseDouble((String) rateMap.get("r"));
	// // break;
	// // }
	// // }
	// if (m < 0.1) {
	// // TODO 山重： 如果当期租金已经核销完，与原罚息一致，不做增加
	// } else {
	// if (simple) {
	// xyjfx = Finance.cfx(m, f, d, rate);
	// } else {
	// xyjfx = Finance.sfx(m, f, d, rate);
	// }
	// System.out.println("m:" + m + "f:" + f + "d:" + d + "rate:" + rate +
	// "xyjfx:" + xyjfx);
	// double PENALTY_RECE = ((BigDecimal)
	// item.get("D_RECEIVE_MONEY")).doubleValue() + xyjfx;
	// due.put("PENALTY_RECE", PENALTY_RECE);
	// due.put("PENALTY_PAID", ((BigDecimal)
	// item.get("D_RECEIVE_MONEY")).doubleValue());
	// }
	// // 判断是否存在今天的罚息记录
	// Dao.insert("fi.overdue.addOverdue", due);
	// }
	// }
	// }

	// ------------------------------* 供应商逾期一览（包括虚拟核销消除逾期的统计）
	// *------------------------------------//

	public Page query_Supp_OverDue_Page(Map<String, Object> m) {
		Map SUP_MAP = BaseUtil.getSup();
//		if (SUP_MAP != null) {
//			m.put("SUP_ID", SUP_MAP.get("SUP_ID"));
//		}

//		Map COM_MAP = BaseUtil.getCom();
//		if (COM_MAP != null) {
//			m.put("COMPANY_ID", COM_MAP.get("COMPANY_ID"));
//		}
		return PageUtil.getPage(m, "fi.overdue.query_Supp_OverDue_Page",
				"fi.overdue.query_Supp_OverDue_Page_count");

	}

	// 查询区域list
	public List query_Area_List() {
		return Dao.selectList("fi.overdue.query_Area_List");
	}

	// 查询合计
	public Map query_OverDue_All(Map map) {
		return Dao.selectOne("fi.overdue.query_Supp_All");
	}

	// 导出
	public Excel overDueUpload() {
		List<Map<String, Object>> dataList = Dao
				.selectList("fi.overdue.query_List");

		Excel excel = new Excel();
		excel.addSheetName("sheet01");
		excel.addData(dataList);
		LinkedHashMap<String, String> title = new LinkedHashMap<String, String>();

		title.put("ROW_NUM", "序号");
		title.put("AREA_NAME", "区域");
		title.put("SUP_NAME", "供应商");
		title.put("COMPANY_NAME", "厂商");
		title.put("AMOUNTALL", "在租台量");
		title.put("AMOUNTOVERDUE", "逾期台量");
		title.put("EQOVERDUE", "逾期率 %");
		title.put("PAID_MONEY_ALL", "租金金额");
		title.put("OVERDUE_MONEY_ALL", "逾期租金金额");
		title.put("PRICEOVERDUE", "租金逾期率 %");
		excel.addTitle(title);

		excel.setHasTitle(true);
		return excel;
	}

	// ------------------------------* 供应商逾期一览（包括虚拟核销消除逾期的统计）
	// *------------------------------------//

	// ------------------------------* 租金催收（催收客户，不需要柔和虚拟核销）
	// *------------------------------------//

	public Page query_overdue_Price_MG(Map<String, Object> m) {
		if (m != null && m.get("SUP_NAME") != null
				&& m.get("SUP_NAME").equals("超级管理员")) {
			m.remove("SUP_NAME");
		}
		assert m != null;
		System.out.println("M:" + m.get("SUP_NAME"));
		Map SUP_MAP = BaseUtil.getSup();
//		if (SUP_MAP != null) {
//			m.put("SUP_ID", SUP_MAP.get("SUP_ID"));
//		}
//		System.out.println("SUP_MAP:"+SUP_MAP);
		return PageUtil.getPage(m, "fi.overdue.query_overdue_Price_MG",
				"fi.overdue.query_overdue_Price_MG_count");
	}

	public Page query_overdue_Price_MG_SUPP(Map<String, Object> m) {
		if (m != null && m.get("SUP_NAME") != null
				&& m.get("SUP_NAME").equals("超级管理员")) {
			m.remove("SUP_NAME");
		}
		Map<String, Object> SUP_MAP = BaseUtil.getSup();
//		if (SUP_MAP != null) {
//			m.put("SUP_ID", SUP_MAP.get("SUP_ID"));
//		}
		return PageUtil.getPage(m, "fi.overdue.query_overdue_Price_MG_SUPP",
				"fi.overdue.query_overdue_Price_MG_SUPP_count");
	}

	public Map<String, Object> getemployee(Map<String, Object> map) {
		Map<String, Object> m = Dao.selectOne("fi.overdue.getemployee", map);
		if (m != null) {
			Dao.getClobTypeInfo2(m, "IDCARD_PHOTO");
		}
		return m;
	}

	// 获取省市
	public Object getProvince() {
		return Dao.selectList("customers.getProvince");
	}

	public Object getCity(Map<String, Object> map) {
		return Dao.selectList("customers.getCity", map);
	}

	// 担保信息
	public List<Map<String, Object>> getGuarantorList(Map<String, Object> param) {
		return Dao.selectList("fi.overdue.getGuarantorList", param);
	}

	// 资信
	public Map<String, Object> getCredit(Map<String, Object> param) {
		return Dao.selectOne("fi.overdue.getCredit", param);
	}

	// 客户资料
	@SuppressWarnings("unchecked")
	public Object findCustDataType() {
		Map<String, Object> type = new HashMap<String, Object>();
		// 客户类型判断
		String TYPE = "客户类型";
		List<Object> list = (List) new DataDictionaryMemcached().get(TYPE);
		type.put("list", list);

		// 证件类型
		String id_type = "证件类型";
		List<Object> id_typeL = (List) new DataDictionaryMemcached().get(id_type);
		type.put("id_typeL", id_typeL);

		// 公司性质
		String com_type = "公司性质";
		List<Object> com_typeL = (List) new DataDictionaryMemcached().get(com_type);
		type.put("com_typeL", com_typeL);

		// 民族
		List<Object> nationL = (List) new DataDictionaryMemcached().get("民族");
		type.put("nationL", nationL);

		// 本地户籍
		List<Object> register = (List) new DataDictionaryMemcached().get("本地户籍");
		type.put("register", register);

		// 纳税资质
		List<Object> seniority = (List) new DataDictionaryMemcached().get("纳税资质");
		type.put("seniority", seniority);

		// 纳税情况
		List<Object> situation = (List) new DataDictionaryMemcached().get("纳税情况");
		type.put("situation", situation);

		// 婚姻状况
		List<Object> marital_status = (List) new DataDictionaryMemcached().get("婚姻状况");
		type.put("marital_status", marital_status);

		// 文化程度
		List<Object> degree_edu = (List) new DataDictionaryMemcached().get("文化程度");
		type.put("degree_edu", degree_edu);

		// 从事职业
		List<Object> profession = (List) new DataDictionaryMemcached().get("职务");
		type.put("profession", profession);

		// 行业分类
		List<Object> INDUSTRY_FICATION_List = (List) new DataDictionaryMemcached().get("行业类型");
		type.put("INDUSTRY_FICATION_List", INDUSTRY_FICATION_List);

		// 企业规模
		List<Object> SCALE_ENTERPRISE_List = (List) new DataDictionaryMemcached().get("企业规模");
		type.put("SCALE_ENTERPRISE_List", SCALE_ENTERPRISE_List);

		// 兴趣爱好
		List<Object> XQAH_List = (List) new DataDictionaryMemcached().get("兴趣爱好");
		type.put("XQAH_List", XQAH_List);
		// 性格
		List<Object> XG_List = (List) new DataDictionaryMemcached().get("性格");
		type.put("XG_List", XG_List);
		// 身体状态
		List<Object> STZT_List = (List) new DataDictionaryMemcached().get("身体状态");
		type.put("STZT_List", STZT_List);
		return type;
	}

	public Object getOverdueCollectRecordByPayCode(Map<String, Object> map) {
		return Dao.selectList("fi.overdue.getOverdueCollectRecordByRenterCode",
				map);
	}

	// 添加催收记录
	public int doAddPressDunLog(Map<String, Object> map) {
		return Dao.insert("fi.overdue.doAddPressDunLog", map);
	}

	// 查询刚刚添加的催收记录的ID
	public int selectDunLogPreId() {
		Map<String, Object> str =  Dao.selectOne("fi.overdue.selectDunLogPreId", null);
		return Integer.parseInt(str.get("CURRVAL").toString());
	}

	// 添加催收记录的录音文件信息
	public int doAddCollectionRecord(Map<String, Object> map) {
		return Dao.insert("fi.overdue.doAddCollectionRecord", map);
	}

	// 删除催收记录
	public int doDeletePressDunLog(String ID) {
		return Dao.delete("fi.overdue.doDeletePressDunLog", ID);
	}

	public List DunLog_More(Map<String, Object> map) {
		return Dao.selectList("fi.overdue.DunLog_more", map);
	}

	// 逾期明细
	public List query_overdue_All(Map<String, Object> map) {
		return Dao.selectList("fi.overdue.query_overdue_All", map);
	}

	// 还款明细
	public List STATEMENTS_ALL(Map<String, Object> map) {
		return Dao.selectList("fi.overdue.STATEMENTS_ALL", map);
	}

	// 转诉讼
	public int subLawsuit(Map<String, Object> map) {
		return Dao.update("fi.overdue.updateLawsuit_Pay_Plan", map);
	}

	// 发送短信
	public int sendSMS(Map<String, Object> map) {
		Map mapSend = Dao.selectOne("fi.overdue.queryPhoneByPay", map);
		Map dunSend = Dao.selectOne("fi.overdue.query_dun_All", map);

		Map<String, Object> mapSendMs = new HashMap<String, Object>();
		if (mapSend != null && mapSend.get("PHONE") != null
				&& !mapSend.get("PHONE").equals("")) {
			mapSendMs.put("MOBILE", mapSend.get("PHONE"));
			String project_code = (mapSend.get("PRO_CODE") != null && !mapSend
					.get("PRO_CODE").equals("")) ? mapSend.get("PRO_CODE")
					.toString() : "";
			String PERIODS = (dunSend.get("PERIODS") != null && !dunSend.get(
					"PERIODS").equals("")) ? dunSend.get("PERIODS").toString()
					: "";
			String PENALTY_ALL = (dunSend.get("PENALTY_ALL") != null && !dunSend
					.get("PENALTY_ALL").equals("")) ? dunSend
					.get("PENALTY_ALL").toString() : "";
			String message = "友情提醒:尊敬的客户,贵方合同号为" + project_code + "的第"
					+ PERIODS + "期租金因逾期产生违约金" + PENALTY_ALL
					+ "元,请您按时还款，避免法律责任。最终还款依据以上述合同为准,如您已足额存款,可不予理会.";
			mapSendMs.put("CONTENT", message);
			return Dao.insert("fi.overdue.insert_SMS_Mes", mapSendMs);
		}
		return 0;
	}

	// ------------------------------* 租金催收（催收客户，不需要柔和虚拟核销）
	// *------------------------------------//

	// ------------------------------* 资产（资产一览，逾期不需要柔和虚拟核销）
	// *------------------------------------//
	public Page query_asset_MG(Map<String, Object> m) {
		return PageUtil.getPage(m, "asset.asset_Msg", "asset.asset_Msg_count");
	}

	// public void exemptOverdue(Map<String, Object> param) {
	// String id = Dao.getSequence("SEQ_FI_OVERDUE_EXEMPT");
	// param.put("ID", id);
	// Dao.insert("fi.overdue.addExemptOverdue", param);
	// }

	/**
	 * 可免息列表
	 *
	 * @param map
	 * @return
	 */
	public Page getExemptPage(Map<String, Object> map) {
		return PageUtil.getPage(map, "fi.overdue.getExemptPage",
				"fi.overdue.getExemptPageCount");
	}

	public Map<String, Object> getBpmInfoByPayCode(Map<String, Object> param) {
		return Dao.selectOne("fi.overdue.getBpmInfoByPayCode", param);
	}

	public void upExemptOverdue(Map<String, Object> param) {
		Dao.update("fi.overdue.upExemptOverdue", param);
	}

	public Object getExempt(Map<String, Object> param) {
		if (param.get("ID") == null || "".equals(param.get("ID"))) {
			param.put("ID", param.get("id"));
		}
		if (param.get("ID") == null || "".equals(param.get("ID"))) {
			param.put("ID", param.get("EXEMPT_ID"));
		}
		return Dao.selectOne("fi.overdue.getExempt", param);
	}

	public Page getExemptApplyPage(Map<String, Object> map) {
		Page page = new Page(map);
		List<Map<String, Object>> l = Dao.selectList(
				"fi.overdue.getExemptApplyPage", map);
		for (Map<String, Object> map2 : l) {
			map2.put("SUMFUN_C", UTIL.FORMAT.currency(map2.get("SUMFUN")));
		}
		page.addDate(JSONArray.fromObject(l), Dao.selectInt(
				"fi.overdue.getExemptApplyPageCount", map));
		return page;
	}

	public void exemptOverdue(String id, String pid) {
		Map<String, Object> m = Dao
				.selectOne("fi.overdue.getApplyHisByOID", id);
		if (m != null) {
			throw new ActionException(m.get("PAY_CODE") + " " + m.get("PERIOD")
					+ "期 已经申请，请勿重复提交");
		}
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("PID", pid);
		map.put("OID", id);
		int i = Dao.insert("fi.overdue.addExemptOverdueItem", map);
		Dao.update("fi.overdue.appOverdueUpStatus", id);
		// if (i == 0) { throw new ActionException("未找到对应罚息数据,请重新确认"); }
		if (i == 0) {
			throw new ActionException("该数据不能发起减免流程。如需减免，请先核销该期租金在进行减免。");
		}
	}

	public void joinDataClean(String id) {
		List<Map<String, Object>> list = Dao.selectList(
				"fi.overdue.getExemptOpList2", id);
		for (Map<String, Object> mapDate : list) {
			Dao.delete("rentWrite.deleteJoinDate", mapDate);
			Dao.insert("rentWrite.insertJoinDate", mapDate);
		}
	}

	public void exemptOverdueAll(Map<String, Object> map) {
		int i = Dao.insert("fi.overdue.addExemptOverdueItemAll", map);
		// if (i == 0) { throw new ActionException("未找到对应罚息数据,请重新确认"); }
		if (i == 0) {
			throw new ActionException("该数据不能发起减免流程。如需减免，请先核销该期租金在进行减免。");
		}

		Dao.update("fi.overdue.appOverdueUpStatusAll", map);
	}

	public void createExemptApply(Map<String, Object> param) {
		String id = Dao.getSequence("SEQ_FI_OVERDUE_EXEMPT");
		param.put("ID", id);
		param.put("PID", id);
		Dao.insert("fi.overdue.addExemptOverdue", param);
	}

	public Object getExemptList(Map<String, Object> map) {
		return Dao.selectList("fi.overdue.getExemptList", map);
	}

	public void exemptItemUp(Map<String, Object> param) {
		Dao.update("fi.overdue.upExemptOverdueItem", param);
	}

	// ------------------------------* 资产（资产一览，逾期不需要柔和虚拟核销）
	// *------------------------------------//

	/**
	 * 删除逾期数据所有（根据还款计划编号）
	 *
	 * @param paycode
	 */
	public void deleteByPay(String paycode) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("PAY_CODE", paycode);
		Dao.delete("fi.overdue.deleteByPay0", param);
	}

	/**
	 * 删除逾期数据（根据还款计划编号和期次）
	 *
	 * @param paycode
	 * @param period
	 */
	public void deleteByPay(String paycode, String period) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("PAY_CODE", paycode);
		param.put("PERIOD", period);
		Dao.delete("fi.overdue.deleteByPay1", param);
	}

	/**
	 * 删除逾期数据大于等于传入期次（根据还款计划编号和期次）
	 *
	 * @param paycode
	 * @param period
	 */
	public void deleteByPayAF(String paycode, String period) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("PAY_CODE", paycode);
		param.put("PERIOD", period);
		Dao.delete("fi.overdue.deleteByPay2", param);
	}

	/**
	 * 删除逾期数据大于等于传入期次时间（根据还款计划编号和期次）
	 *
	 * @param paycode
	 * @param date
	 */
	public void deleteByPayEAFDate(String paycode, String date) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("PAY_CODE", paycode);
		param.put("CREATE_DATE", date);
		Dao.delete("fi.overdue.deleteByPay3", param);
	}

	/**
	 * 删除逾期数据大于传入期次时间（根据还款计划编号和期次）
	 *
	 * @param paycode
	 * @param date
	 */
	public void deleteByPayAFDate(String paycode, String date) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("PAY_CODE", paycode);
		param.put("CREATE_DATE", date);
		Dao.delete("fi.overdue.deleteByPay4", param);
	}

	/**
	 *
	 * @Title: over_Mg_AJAX 风险成因管理
	 * @autor wuguowei_jing@163.com 2014-5-14 下午3:16:51
	 */
	public Page over_Mg_AJAX(Map<String, Object> m) {
		return PageUtil.getPage(m, "fi.overdue.over_Mg_AJAX",
				"fi.overdue.over_Mg_AJAX_count");

	}

	// Add By YangJ 2014年5月15日11:01:31 查询asset资产明细
	public List<Map<String, Object>> query_asset_Detail(Map<String, Object> m) {// System.out.println("service调用mm "+m);
		return Dao.selectList("fi.overdue.queryAssetDetail", m);
	}

	// // Add By YangJ 2014年5月15日20:02:52 查询 省级行政区划
	// public List getFilSystemArea(){
	// return Dao.selectList(".getFilSystemArea");
	// }
	// //Add By YangJ 2014年5月15日20:03:20 查询GPS地点信息
	// public List selectDeviceGps(Map param){
	// return Dao.selectList("fi.overdue.gps.selectDeviceGps",param);
	// }
	//

	// 统计图
	public int getStatistics(String REPAYMENT_TYPE) {
		return Dao
				.selectInt("OverdueRecordpress.getStatistics", REPAYMENT_TYPE);
	}

	// 查看消除原因
	public List getRemoveData(Map<String, Object> m) {
		return Dao.selectList("fi.overdue.getRemoveData", m);
	}

	public int addRemoveData(Map<String, Object> m) {
		return Dao.update("fi.overdue.addRemoveData", m);
	}

	// 查询该支付表所选择的设备
	public List<Map<String, Object>> queryEqByProjectID(
			Map<String, Object> param) {
		param.put("tags", "设备单位");
		return Dao.selectList("project.queryEqByProjectID", param);
	}

	// 查询该支付表还款情况
	public List<Map<String, Object>> getRepayment(Map<String, Object> param) {
		return Dao.selectList("fi.overdue.getRepayment", param);
	}

	/**
	 * 查看还款计划明细页面的头信息
	 *
	 * @param map
	 * @author King 2013-9-28
	 * @return
	 */
	public Map<String, Object> doShowRentHeadByPayId(Map<String, Object> map) {
		map.put("_TYPE", "租赁周期");
		return Dao.selectOne("fi.overdue.queryRentHead", map);
	}

	public List<Map<String, Object>> doShowRentDetailOtherFeeListByPayId(
			Map<String, Object> map) {
		map.put("_TYPE", "应收项目类型");
		return Dao.selectList("fi.overdue.doShowRentDetailOtherFeeListByPayId",
				map);
	}

	// 通过合同ID获取 paymentdetail 列表
	public List<Map<String, Object>> getPaymentDetailsByBuyContractId(
			Map<String, Object> map) {
		return Dao.selectList("fi.overdue.getPaymentDetailsByBuyContractId",
				map);
	}

	// 通过paymentDetailId获取paymentTerm列表
	public List<Map<String, Object>> getPaymentTermsByPaymentDetailId(
			Map<String, Object> param) {
		return Dao.selectList("paymentTerm.getPaymentTermsByPaymentDetailId",
				param);
	}

	public List<Map<String, Object>> queryPaymentMouldDetail(
			Map<String, Object> m) {
		if (m == null || m.get("PLATFORM_TYPE") == null)
			return null;

		return Dao.selectList("PayTask.queryPaymentMouldDetailByType", m);
	}

	// 通过Id查询
	public Map<String, Object> getBuyContractAndFilRentPlanHeadById(
			Map<String, Object> m) {
		return Dao.selectOne("fi.overdue.getBuyContractAndFilRentPlanHeadById",
				m);
	}

	public void addUrgeData(Map<String, Object> m) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.putAll(m);
		Object type = map.get("TYPE");
		String DATA_ID = "";
		Map<String, Object> urgeData = Dao.selectOne("fi.overdue.getUrgeData", map);
		if(urgeData != null){
			String url1 = "crm/OverdueUrge";
			String url2 = ".action?DATA_ID=";
			String url = "";
			map.putAll(urgeData);
			map.put("PAY_CODE", map.get("PAYLIST_CODE"));
			map.put("PROJECT_ID", map.get("PROJECT_ID"));
			logger.info(map);
			if("1".equals(type)){//电催
				DATA_ID = Dao.getSequence("SEQ_RE_OVERDUE_COLL_PHONE");
				map.put("DATA_ID", DATA_ID);
				Dao.insert("fi.overdue.saveUrgePhone",map);
				map.put("TASK_NAME", "电话催收");
				url = url1 +"!urgePhone"+ url2;

			}else if("2".equals(type)){//函件
				DATA_ID = Dao.getSequence("SEQ_RE_OVERDUE_COLL_LETTER");
				map.put("DATA_ID", DATA_ID);
				Dao.insert("fi.overdue.saveUrgeLetter",map);
				map.put("TASK_NAME", "函件催收");
				url = url1 + url2;

			}else if("3".equals(type)){//法务催收
				DATA_ID =  Dao.getSequence("SEQ_RE_OVERDUE_COLL_LAWYER");
				map.put("DATA_ID", DATA_ID);
				Dao.insert("fi.overdue.saveUrgeLawyer",map);
				map.put("TASK_NAME", "法务催收");
				url = url1 +"!urgeLawyer"+ url2;

			}else if("4".equals(type)){//强制催收
				DATA_ID = Dao.getSequence("SEQ_RE_OVERDUE_COLL_FORCE");
				map.put("DATA_ID", DATA_ID);
				Dao.insert("fi.overdue.saveUrgeForce",map);
				map.put("TASK_NAME", "强制催收");
				url = url1 +"!urgeForce"+ url2;
			}
			map.put("DATA_NAME", "分配催收任务");
			map.put("URL", url+DATA_ID);
			map.put("CLAIM_ID", Dao.getSequence("SEQ_T_SYS_TASK_CLAIM"));
			Dao.insert("fi.overdue.saveUrgeLog",map);
			map.put("TYPE", 2);
//			new TaskClaimService().addClaim(map);
			Dao.insert("fi.overdue.saveTaskManData",map);
		}


	}

	public List<Map<String, Object>> getData(Map<String, Object> param) {
		if (param != null && param.get("SUP_NAME") != null && param.get("SUP_NAME").equals("超级管理员")) {
			param.remove("SUP_NAME");
		}
		Map<String, Object> SUP_MAP = BaseUtil.getSup();
//		if (SUP_MAP != null) {
//			param.put("SUP_ID", SUP_MAP.get("SUP_ID"));
//		}
		assert param != null;
		param.put("PAGE_END", param.get("rows"));
		param.put("PAGE_BEGIN", param.get("page"));
		return Dao.selectList("fi.overdue.query_overdue_Price_MG", param);
	}

	public Map<String, Object> queryEqByOverdueID(Map<String, Object> param) {
		if (param != null && param.get("SUP_NAME") != null && param.get("SUP_NAME").equals("超级管理员")) {
			param.remove("SUP_NAME");
		}
		Map<String, Object> SUP_MAP = BaseUtil.getSup();
//		if (SUP_MAP != null) {
//			param.put("SUP_ID", SUP_MAP.get("SUP_ID"));
//		}
		assert param != null;
		param.put("PAGE_END", param.get("rows"));
		param.put("PAGE_BEGIN", param.get("page"));
		return Dao.selectOne("fi.overdue.query_overdue_price_copy", param);

	}
}