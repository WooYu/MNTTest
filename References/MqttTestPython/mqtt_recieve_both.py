#!/usr/bin/python
# -*- coding: utf-8 -*-
# @File  : mqtt_recieve.py
# @Author: liwang
# @Time  : 2024-10-31 20:04
import socket
import sys

import paho.mqtt.client as mqtt
import argparse
import threading
import string
import random
import time

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
        length = self.random.randint(min_length, max_length)
        return  'receieve'.join(self.random.choices(self.letters, k=length-8))

def on_connect(client, userdata, flags, rc, properties=None):
    logger.info(f"Connected with result code {rc}")
    if rc == 0:
        client.subscribe(userdata['subscribe_topic'])
    else:
        logger.error(f"Connection failed with code {rc}")

def on_message(client, userdata, msg):
    # logger.info(f"{userdata['client_id']} received length: {len(msg.payload.decode())} from topic: {msg.topic}")
    # logger.info(f"{userdata['client_id']} received length: {len(msg.payload.decode())}")
    client.publish(userdata['publish_topic'], msg.payload.decode(), qos=0)

    # logger.info(f"{userdata['client_id']} replied with: {response} to topic: {userdata['response_topic']}")
    userdata['received_count'] += 1
    if userdata['received_count'] % 1000 == 0:
        logger.info(f"received count: {userdata['received_count']}")
    logger.debug(f"received count: {userdata['received_count']}")
    if userdata['received_count'] >= userdata['message_count']:
        logger.info(f"{userdata['client_id']} received enough messages, disconnecting...")
        client.disconnect()
def on_socket_open(client, userdata,sock):
    logger.info("socket opened")
    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    # sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_QUICKACK, 1)

def start_client(client_id, publish_topic, subscribe_topic, message_count, broker, port, username, password):
    userdata = {'client_id': client_id, 'subscribe_topic': subscribe_topic, 'publish_topic': publish_topic, 'received_count': 0, 'message_count': message_count}
    client = mqtt.Client(client_id=client_id, userdata=userdata, protocol=mqtt.MQTTv311, transport='tcp')
    client.username_pw_set(username, password)
    client.on_connect = on_connect
    client.on_message = on_message
    client.on_socket_open = on_socket_open

    client.connect(broker, port, 60)
    client.loop_forever()

def main(args):
    threads = []
    for i in range(args.clients):
        if args.token:
            token = args.token
        else:
            token = get_token(args.env, args.recieve_sn, args.sn_pwd, args.sn_mac_address)
        t = threading.Thread(target=start_client, args=(args.recieve_sn, args.send_sn, args.recieve_sn, args.msg_count, args.ip, args.port, envs[args.env], token))
        t.start()
        threads.append(t)

    for thread in threads:
        thread.join()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="MQTT Subscriber")
    parser.add_argument("--ip", type=str, required=True, help="MQTT ip address")
    parser.add_argument("--port", type=int, default=1883, help="MQTT broker port")
    parser.add_argument("--env", type=str, default="", required=True, help="MQTT broker env")
    parser.add_argument("--token", type=str, default="", help="MQTT broker password")
    parser.add_argument("--sn_pwd", type=str, default="", help="sn_pwd")
    parser.add_argument("--sn_mac_address", type=str, default="", help="sn_mac_address")
    parser.add_argument("--send_sn", type=str, required=True, help="MQTT topic to publish to")
    parser.add_argument("--recieve_sn", type=str, required=True, help="MQTT topic to subscribe to")
    parser.add_argument("--msg_count", type=int, default="", required=True, help="Message count")
    parser.add_argument("--clients", type=int, default=1, help="Number of subscriber clients to start")
    parser.add_argument("--log_level", type=str, default="INFO", help="log level")
    # parser.add_argument("--client-prefix", type=str, default="mqtt_sub", required=True, help="Prefix for client IDs")
    args = parser.parse_args()
    generator = RandomStringGenerator(include_special_chars=False)
    logger.remove()
    logger.add(sys.stdout, format="{time} | {level} - {message}", level=args.log_level)
    logger.add(r'./log/' + args.ip + '_recieve_{time}.log', format="{time} | {level} - {message}", rotation="50 MB",
               encoding='utf-8', level=args.log_level)

    main(args)

