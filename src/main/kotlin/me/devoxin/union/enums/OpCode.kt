package me.devoxin.union.enums

enum class OpCode {
    UNUSED,
    HELLO, // send only
    MEMBER_ADD, // send only
    MESSAGE, // send only
    PRESENCE_UPDATE, // send only
    SERVER_JOIN, // send only
    SERVER_LEAVE, // send only
    MEMBER_CHUNK, // send only
    DELETE_MESSAGE, // send only
    MEMBER_LEAVE, // send only
    HEARTBEAT, // send only
    HEARTBEAT_ACK; // receive only
}
