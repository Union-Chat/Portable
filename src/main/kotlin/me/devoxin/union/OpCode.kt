package me.devoxin.union

enum class OpCode {
    UNUSED,
    HELLO,
    MEMBER_ADD,
    MESSAGE,
    PRESENCE_UPDATE,
    SERVER_JOIN,
    SERVER_LEAVE,
    MEMBER_CHUNK,
    DELETE_MESSAGE,
    MEMBER_LEAVE;
}
