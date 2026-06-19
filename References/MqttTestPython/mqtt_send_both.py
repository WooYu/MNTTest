#!/usr/bin/python
# -*- coding: utf-8 -*-
# @File  : mqtt_send.py
# @Author: liwang
# @Time  : 2024-10-31 20:03
import random
import socket
import string
import sys
import time
import threading
from datetime import datetime

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

class RandomStringGenerator:
    def __init__(self, include_special_chars=False):
        self.letters = string.ascii_letters + string.digits
        if include_special_chars:
            self.letters += string.punctuation
        self.random = random.SystemRandom()

    def generate(self, min_length, max_length):
        #length = self.random.randint(min_length, max_length)
        #return str(int(time.time()*1000)) + ''.join(self.random.choices(self.letters, k=length-13))
        return str(int(time.time()*1000)) + 'AAAAAAAAAAAAAAAAA'


def timestamp_to_ymdhms_ms(timestamp):
    # 如果时间戳是毫秒级别的，先转换为秒级别
    timestamp = timestamp / 1000.0

    # 使用 time.localtime() 将秒级别的时间戳转换为本地时间的 struct_time 对象
    time_struct = time.localtime(timestamp)

    # 使用 time.strftime() 将 struct_time 对象格式化为指定的字符串格式
    formatted_time = time.strftime('%Y/%m/%d %H:%M:%S', time_struct)

    # 获取毫秒部分
    milliseconds = int((timestamp % 1) * 1000)

    return str(f"{formatted_time}:{milliseconds:03d}")

# def timestamp_to_ymdhms_ms(timestamp):
#     # 如果时间戳是毫秒级别的，先转换为秒级别
#     timestamp = timestamp / 1000.0
#
#     # 将时间戳转换为datetime对象
#     dt = datetime.fromtimestamp(timestamp)
#
#     # 格式化datetime对象为所需的格式
#     return dt.strftime('%Y/%m/%d %H:%M:%S.%f').format('YYYY/MM/DD HH:mm:ss.SSS')

def on_connect(client, userdata, flags, rc, properties=None):
    logger.info(f"Connected with result code {rc}")
    if rc == 0:
        # logger.info(f"{userdata['client_id']} subscribed to {userdata['subscribe_topic']}")
        client.subscribe(userdata['subscribe_topic'])
        userdata['event'].set()  # 设置事件，通知可以开始发送数据
    else:
        logger.error("Connection failed")


def on_message(client, userdata, msg):
    # logger.info(f"{userdata['client_id']} received length: {len(msg.payload.decode())} from topic: {msg.topic}")
    # if _msg[:8] == "receieve" :
    #     return
    # _time = int(_msg[:13])
    # logger.warning(f"{timestamp_to_ymdhms_ms(_time)},{str(_end_time-_time)}, {str(_msg)[:20]}")
    userdata['received_count'] += 1
    if userdata['received_count'] % 1000 == 0:
        _end_time = int(time.time()*1000)
        _msg = msg.payload.decode()
        logger.info(f"received count: {userdata['received_count']}")
        _time = int(_msg[:13])
        logger.warning(f"{timestamp_to_ymdhms_ms(_time)},{str(_end_time-_time)}, {str(_msg)[:20]}")
    logger.debug(f"received count: {userdata['received_count']}")
    if userdata['received_count'] >= userdata['message_count']:
        logger.info(f"{userdata['client_id']} received enough messages, disconnecting...")
        client.disconnect()


def publish_messages(client, userdata, interval, message_count, message_min_length, message_max_length):
    # 等待连接成功的事件
    userdata['event'].wait()
    count = 0
    batch_count = 0
    while count < message_count:
        while batch_count < 5:
            #generator.generate(message_min_length, message_max_length)
            message = str(int(time.time()*1000)) + "AAA" + str(batch_count)
            client.publish(userdata['publish_topic'], message)
            #logger.info(f"{userdata['client_id']} published: {message} to {userdata['publish_topic']}")
            count += 1
            batch_count += 1
            if count % 10000 == 0:
                logger.info(f"send count: {count}")
            logger.debug(f"send count: {count}")
        batch_count = 0
        time.sleep(random.randint(1, interval)/1000)
    logger.info(f"{userdata['client_id']} has finished publishing {message_count} messages.")

def custom_ssl_socket_factory():
    # 创建原始TCP socket
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    # 设置TCP_NODELAY
    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)

    # 如果Broker使用自签名证书，你可能需要禁用证书验证（仅用于测试）
    # context.check_hostname = False
    # context.verify_mode = ssl.CERT_NONE

    return sock

def on_disconnect(client, userdata, rc):
    logger.info("disconnect ...")

def on_socket_open(client, userdata,sock):
    logger.info("socket opened")
    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    # sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_QUICKACK, 1)


def start_client(client_id, publish_topic, subscribe_topic, interval, message_count, message_min_length, message_max_length, broker, port, username,
                 password):
    userdata = {
        'client_id': client_id,
        'publish_topic': publish_topic,
        'subscribe_topic': subscribe_topic,
        'received_count': 0,
        'message_count': message_count,
        'event': threading.Event()
    }
    client = mqtt.Client(client_id=client_id, userdata=userdata, protocol=mqtt.MQTTv311, transport='tcp')
    client.username_pw_set(username, password)
    client.on_connect = on_connect
    client.on_message = on_message
    client.on_disconnect = on_disconnect
    client.on_socket_open = on_socket_open
    client.connect(broker, port, 60)
    publish_thread = threading.Thread(target=publish_messages, args=(client, userdata, interval, message_count, message_min_length, message_max_length))
    publish_thread.start()

    client.loop_forever()


def main(args):
    threads = []
    for i in range(args.clients):
        if args.token:
            token = args.token
        else:
            token = get_token(args.env, args.send_sn, args.sn_pwd, args.sn_mac_address)
        t = threading.Thread(target=start_client, args=(
            args.send_sn, args.recieve_sn, args.send_sn, args.interval, args.msg_count,
            args.msg_min_length, args.msg_max_length, args.ip, args.port, envs[args.env], token))
        t.start()
        threads.append(t)

    for thread in threads:
        thread.join()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="MQTT Publisher and Subscriber")
    parser.add_argument("--ip", type=str, required=True, help="MQTT ip address")
    parser.add_argument("--port", type=int, default=1883, help="MQTT broker port")
    parser.add_argument("--env", type=str, default="", help="MQTT broker env")
    parser.add_argument("--token", type=str, default="", help="MQTT broker password")
    parser.add_argument("--sn_pwd", type=str, default="", help="sn_pwd")
    parser.add_argument("--sn_mac_address", type=str, default="", help="sn_mac_address")
    parser.add_argument("--send_sn", type=str, required=True, help="MQTT topic to publish to")
    parser.add_argument("--recieve_sn", type=str, required=True, help="MQTT topic to subscribe to")
    parser.add_argument("--interval", type=int, default=1, help="Interval between messages in milliseconds")
    parser.add_argument("--msg_count", type=int, default=1, help="Number of messages to send")
    parser.add_argument("--msg_min_length", type=int, default=15, help="message_min_length")
    parser.add_argument("--msg_max_length", type=int, default=20, help="message_max_length")
    parser.add_argument("--clients", type=int, default=1, help="Number of clients to start")
    parser.add_argument("--log_level", type=str, default="INFO", help="log level")
    # parser.add_argument("--client-prefix", type=str, default="mqtt_client", help="Prefix for client IDs")
    generator = RandomStringGenerator(include_special_chars=False)
    args = parser.parse_args()

    logger.remove()
    logger.add(sys.stdout, format="{time} | {level} - {message}", level=args.log_level, filter=lambda record: record["level"].name == args.log_level)
    logger.add(r'./log/{}_interval{}ms'.format(args.ip, args.interval) + '_send_{time}.log', format="{time} | {level} - {message}", rotation="500 MB", encoding='utf-8',
               level=args.log_level)
    logger.add(r'./result/{}_interval{}ms'.format(args.ip, args.interval) + '_send_{time}.csv', format="{message}", rotation="500 MB", encoding='utf-8', level="WARNING",
               filter=lambda record: record["level"].name == "WARNING")

    main(args)

