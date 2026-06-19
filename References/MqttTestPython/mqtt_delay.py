#!/usr/bin/python
# -*- coding: utf-8 -*-
# @File  : mqtt_send.py
# @Author: liwang
# @Time  : 2024-10-31 20:03
import os
import random
import string
import sys
import time
import threading
from concurrent.futures.thread import ThreadPoolExecutor
from concurrent.futures._base import as_completed

import paho.mqtt.client as mqtt
import argparse

from loguru import logger

from get_token import get_token

envs = {
    "testcn": "https://autel-cloud-gateway-test.auteltech.cn/api",
    "testus": "https://autel-cloud-gateway-testus.autel.com/api",
    "pre"   : "https://autel-cloud-gateway-pre.autel.com/api",
    "prodcn": "https://autel-cloud-gateway-prodcn.auteltech.cn/api",
    "produs": "https://gateway.autel.com/api",
    "prodeu": "https://gateway-prodeu.autel.com/api"
}

def on_connect(client, userdata, flags, rc, properties=None):
    logger.info(f"{userdata['client_id']} Connected with result code {rc}")
    if rc == 0:
        client.subscribe(userdata['topic'])
        userdata['event'].set()  # 设置事件，通知可以开始发送数据
    else:
        logger.error("{userdata['client_id']} Connection failed")


def on_message(client, userdata, msg):
    # logger.info(f"{userdata['client_id']} received msg: {msg.payload.decode()} from topic: {msg.topic}")
    userdata['received_count'] += 1
    if userdata['received_count'] >= userdata['message_count']:
        # logger.info(f"{userdata['client_id']} received enough messages, disconnecting...")
        client.disconnect()


def publish_messages(client, userdata):
    # 等待连接成功的事件
    userdata['event'].wait()
    count = 0
    while True:
        if userdata['message_count'] > 0 and count >= userdata['message_count']:
            break
        message = int(time.time()*1000)
        client.publish(userdata['topic'], message)
        # logger.info(f"{userdata['client_id']} published: {message} to {userdata['publish_topic']}")
        count += 1
        if count % 100 == 0:
            logger.info(f"{userdata['client_id']} send count: {count}")
        logger.debug(f"{userdata['client_id']} send count: {count}")
        time.sleep(int(userdata['interval']/1000))
    logger.info(f"{userdata['client_id']} has finished publishing {userdata['message_count']} messages.")


def start_client(sn, interval, message_count, broker, port, username, password):
    userdata = {
        'client_id': sn,
        'topic': 'diagnosis/testDelay/' + sn,
        'interval': interval,
        'message_count': message_count,
        'received_count': 0,
        'event': threading.Event()
    }
    client = mqtt.Client(client_id=sn, userdata=userdata, protocol=mqtt.MQTTv311, transport='tcp')
    client.username_pw_set(username, password)
    client.on_connect = on_connect
    client.on_message = on_message

    client.connect(broker, port, 60)
    publish_thread = threading.Thread(target=publish_messages, args=(client, userdata))
    publish_thread.start()

    client.loop_forever()

def get_device_info(env, sn, datas):
    token = get_token(env, sn, '', '')
    if token:
        datas.append([sn, token])

def main(args):
    threads = []
    if not os.path.exists("devices.txt"):
        logger.error("devices.txt 文件不存在")
        return
    with open("devices.txt") as fp:
        content = fp.readlines()
    # serial_nos = ["CGJMKAC01002", "CBJMM9C01172"]
    serial_nos = [_.strip() for _ in content if _.strip()]
    logger.info(f"总设备数 {len(serial_nos)}")
    if args.thread_count > len(serial_nos):
        logger.warning(f"总并发数 {args.thread_count} 大于设备数 {len(serial_nos)}，请补充设备")
        return
    devices_info = []
    with ThreadPoolExecutor(max_workers=10) as executor:
        futures = []
        for i in range(args.thread_count):
            future = executor.submit(get_device_info, args.env, serial_nos[i], devices_info)
            futures.append(future)
        for future in as_completed(futures):
            future.result()
    logger.info(f"{len(devices_info)} 个设备鉴权成功")
    logger.debug(devices_info)
    for i in range(args.thread_count):
        t = threading.Thread(target=start_client, args=(
        devices_info[i][0], args.interval, args.msg_count, args.ip, args.port, envs[args.env], devices_info[i][1]))
        threads.append(t)
    for thread in threads:
        thread.start()

    for thread in threads:
        thread.join()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="MQTT Publisher and Subscriber")
    parser.add_argument("--ip", type=str, required=True, help="MQTT ip address")
    parser.add_argument("--port", type=int, default=1883, help="MQTT broker port")
    parser.add_argument("--env", type=str, default="testcn", help="MQTT broker env")
    parser.add_argument("--interval", type=int, default=1000, help="Interval between messages in milliseconds")
    parser.add_argument("--msg_count", type=int, default=10, help="Number of messages to send")
    parser.add_argument("--thread_count", type=int, default=1, help="Number of thread to start")
    parser.add_argument("--range_count", type=int, default=1, help="循环执行次数")
    parser.add_argument("--log_level", type=str, default="INFO", help="log level")
    # parser.add_argument("--client-prefix", type=str, default="mqtt_client", help="Prefix for client IDs")
    args = parser.parse_args()

    logger.remove()
    logger.add(sys.stdout, format="{time} | {level} - {message}", level=args.log_level)
    logger.add(r'./log/{}_threadCount{}_interval{}ms'.format(args.ip, args.thread_count, args.interval) + '_delay_{time}.log', format="{time} | {level} - {message}", rotation="500 MB", encoding='utf-8',
               level=args.log_level)
    if args.range_count == -1:
        while True:
            main(args)
    else:
        for i in range(args.range_count):
            main(args)
    # main(args)

