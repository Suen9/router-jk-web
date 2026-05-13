

# 路由器接口抓包

路由器：**锐捷 RG-MA3063**
IP地址：192.168.10.1

**登录后的接口请求需携带Cookie**

```
__APP_LANG__=zh_cn; SessionTimeout=1000; devHost=cmcc.wifi; tipWireless=true; showProvince=false; SessionID=wDW9ohk4pQMdExfbFx1EOhL4vm9nbk3LF85KSMSdfZ9sDKIeOj1Uyu589403
```

>每次请求携带未过期的SessionID，如果返回code: -1, msg: "AuthRequired",则进行重新授权登录获取新的SessionID覆盖原本的。

## 登录请求

| 请求地址 | 请求方法 | 请求参数                                                     | Content-type                   |
| -------- | -------- | ------------------------------------------------------------ | ------------------------------ |
| /login   | POST     | {"method":"login","params":{"pw":"","un":"admin","time":"1776524601"}} | application/json;charset=utf-8 |

>pw: ff950205603679a12cbd955b1cbf306a
>
>un: admin
>
>time: 1776524601



## 获取终端列表

| 请求地址                         | 请求方法 | Query String Parameters |          Content-Type          |
| -------------------------------- | -------- | :---------------------: | :----------------------------: |
| /api/v1/lua/OnlineUsers/sta_list | GET      |     page=1&size=20      | application/json;charset=utf-8 |

**Response** 

```json
{
    "msg": "",
    "data": {
        "IPV6Mode": 2,
        "total": "",
        "list": [
            {
                "userIp": "",
                "deviceAliasName": "",
                "active": "",
                "rssi": "",
                "hostName": "",
                "flowDown": "",
                "manufacture": "",
                "activeTime": "",
                "onlinetime": "",
                "flowUp": "",
                "userIpV6": "",
                "ssid": "",
                "idx": "",
                "up": "",
                "sn": "",
                "groupId": "",
                "internetaccess": "",
                "groupName": "",
                "osType": "",
                "bannerType": "",
                "wifiUpDown": "",
                "mac": "",
                "band": "",
                "channel": "",
                "rxrate": "",
                "hardwareType": "",
                "down": "",
                "connectType": ""
            }
        ]
    },
    "code": "0"
}
```



## 获取设备信息

| 请求地址                            | 请求方法 | Query String Parameters |          Content-Type          |
| ----------------------------------- | -------- | :---------------------: | :----------------------------: |
| /api/v1/lua/OnlineUsers/getStaItem? | GET      |         idx=15          | application/json;charset=utf-8 |

```json
{
    "msg": "",
    "data": {
        "staItem": {
            "brand": "",
            "mac": "000C29B6F203",
            "connecttype": 0,
            "dsbandwidth": 0,
            "uploadspeed": "0.00",
            "rssi": 0,
            "downloadspeed": "0.00",
            "active": 1,
            "ipaddr": "192.168.10.146",
            "port": 4,
            "inactivetime": "",
            "hostname": "",
            "onlinetime": 241,
            "storageaccess": 1,
            "ipaddr6": "2409:8a55:c41a:2371:c888:8359:877a:6cf7",
            "bytesreceived": "0",
            "usbandwidth": 0,
            "packetssent": "0",
            "ssid": "",
            "crcerror": 0,
            "activetime": "2026-04-18 23:34:31",
            "band": "",
            "packetsreceived": "0",
            "bytessent": "0",
            "vmac": "",
            "internetaccess": 2,
            "ipaddr6_2": "",
            "model": "",
            "idx": 15,
            "devname": "",
            "duplexmode": "Full",
            "negorate": 1000000
        }
    },
    "code": 0
}
```



## 设置限速

| 请求地址                           | 请求方法 |                       Request Payload                        |          Content-Type          |
| ---------------------------------- | -------- | :----------------------------------------------------------: | :----------------------------: |
| /api/v1/lua/OnlineUsers/setStaItem | POST     | {"brand":"","mac":"000C29B6F203","connecttype":0,"dsbandwidth":1,"uploadspeed":"93.36","rssi":0,"downloadspeed":"2911.98","active":1,"ipaddr":"192.168.10.146","port":4,"inactivetime":"","hostname":"","onlinetime":41,"storageaccess":1,"ipaddr6":"fe80::20c:29ff:feb6:f203","bytesreceived":"19614241","usbandwidth":1,"packetssent":"6412","ssid":"","crcerror":0,"activetime":"2026-04-18 23:34:32","band":"","packetsreceived":"13341","bytessent":"478264","vmac":"","internetaccess":2,"ipaddr6_2":"","model":"","idx":15,"devname":"","duplexmode":"Full","negorate":1000000} | application/json;charset=utf-8 |

**response**

```json
{"msg":"","data":"","code":0}
```

>上传限速：usbandwidth
>
>下载限速：dsbandwidth
>
>设备ID：idx
>
>0为不限速



## 接入方式修改

| 请求地址                             | 请求方法 |                       Request Payload                        |          Content-Type          |
| ------------------------------------ | -------- | :----------------------------------------------------------: | :----------------------------: |
| /api/v1/lua/OnlineUsers/set_sta_list | POST     | {"userIp":"192.168.10.146","deviceAliasName":"ruijie","active":1,"rssi":0,"hostName":"","flowDown":"0","manufacture":"0","activeTime":"0","onlinetime":715,"flowUp":"0","userIpV6":"2409:8a55:c41a:2371:c888:8359:877a:6cf7","ssid":"","idx":15,"up":"0","sn":"true","groupId":"0","internetaccess":1,"groupName":"default","osType":"","bannerType":0,"wifiUpDown":"0","mac":"00:0C:29:B6:F2:03","band":"","channel":"auto","rxrate":"","hardwareType":"","down":"0","connectType":0} | application/json;charset=utf-8 |

>internetaccess: 参数如下
>
>2：允许接入并且可以上网
>
>1：允许接入但禁上网
>
>0：不允许接入

**response**

```json
{"msg":"","data":"","code":0}
```



## 踢下线（黑名单）

| 请求地址                                        | 请求方法 |                       Request Payload                        |          Content-Type          |
| ----------------------------------------------- | -------- | :----------------------------------------------------------: | :----------------------------: |
| /api/v1/lua/BlackWhiteList/addwirelessMacFilter | POST     | {"type":"deny","mac":"CA:17:8D:E4:53:78","name":"realme-GT-8"} | application/json;charset=utf-8 |

**response**

```json
{"msg":"","data":"","code":0}
```



## 获取黑名单列表

| 请求地址                                     | 请求方法 | Query String Parameters |          Content-Type          |
| -------------------------------------------- | -------- | :---------------------: | :----------------------------: |
| /api/v1/lua/BlackWhiteList/wirelessMacFilter | GET      |   type=&firstIn=true    | application/json;charset=utf-8 |

**response**

```json
{
    "msg": "",
    "data": {
        "type": "deny",
        "currentTime": "0",
        "groupId": 0,
        "configTime": 1594954329,
        "networkId": "dev_30:0D:9E:48:80:AD_1594954326",
        "version": "1.0.0",
        "configId": "0",
        "subConfigId": "1594954327_G1PD3QN000378",
        "macList": [
            {
                "mac": "5A:C1:DC:66:99:9F",
                "name": "realme-GT-8"
            }
        ]
    },
    "code": 0
}
```



## 删除黑名单

| 请求地址                                           | 请求方法 |                 Request Payload                 |          Content-Type          |
| -------------------------------------------------- | -------- | :---------------------------------------------: | :----------------------------: |
| /api/v1/lua/BlackWhiteList/deletewirelessMacFilter | POST     | {"type":"deny","macList":["5A:C1:DC:66:99:9F"]} | application/json;charset=utf-8 |

**response**

```json
{"msg":"","data":"","code":0}
```

