$(document).ready(function(){
	var data = {};
	$("#pageForm :input").each(function(){
		data[$(this).attr("name")]=$(this).val();
	});
	$("#search").click(function(){
		data = {};
		$("#pageForm :input").each(function(){
			data[$(this).attr("name")]=$(this).val();
		});
		$('#pageTable').datagrid('load', data);
	});
	
	$("#pageTable").datagrid({
		url:_basePath+"/insure/InsureRemind!getPageList.action",
		pageSize:20,
		fit : true,
		selectOnCheck:true,
		pagination : true,
		rownumbers : true,
		queryParams:data,
		toolbar : "#pageForm",
        columns:[[
            {field:'ID',checkbox:true},
            {field:'PRO_CODE',width:100,title:'项目编号'},
            {field:'SUPP_NAME',width:130,title:'供应商'},
            {field:'CUST_NAME',width:150,title:'客户名称'},
            {field:'CUST_NAME_END',width:150,title:'终端客户名称'},
            {field:'EQU_NAME',width:100,title:'租赁物名称'},
            {field:'EQU_TYPE',width:80,title:'租赁物类型'},
            {field:'FACTORY_NO',width:130,title:'出厂编号'},
            {field:'CAR_SYMBOL',width:140,title:'车架号'},
            {field:'EQU_MONEY',width:80,title:'租赁物购买价款'},
            {field:'START_CONFIRM_DATE',width:80,title:'起租确认日'},
            {field:'EST_DATE',width:80,title:'预计投保日期'},
            {field:'YEARNUM',width:80,title:'租赁期限'},
            {field:'PRO_END_DATE',width:80,title:'租赁到期日'}
        ]]
    });
});
function toSup(){
	if(!confirm("确认提交选择数据?")) return;
	var ids = "";
	$.each($('#pageTable').datagrid("getSelections"),function(){
		ids = ids + this.ID+",";
	});
	$.ajax({
		url : _basePath+"/insure/InsureRemind!toSup.action",
		data : "ids="+ids,
		type : "post",
		dataType : "json",
		success : function(json){
			if(json.flag){
				$('#pageTable').datagrid("reload");
			}else{
				alert(json.msg);
			}
		}
	});
}
function toSupAll(){
	if(!confirm("确认批量提交所有数据")) return;
	$.ajax({
		url : _basePath+"/insure/InsureRemind!toSupAll.action",
		type : "post",
		dataType : "json",
		success : function(json){
			if(json.flag){
				$('#pageTable').datagrid("reload");
			}else{
				alert(json.msg);
			}
		}
	});
}
function excel(){
	var ids = "";
	$.each($('#pageTable').datagrid("getSelections"),function(){
		ids = ids + this.ID+",";
	});
	window.open(_basePath+"/insure/InsureRemind!excel.action?ids="+ids);
}

function excelAll(){
	window.open(_basePath+"/insure/InsureRemind!excelAll.action");
}