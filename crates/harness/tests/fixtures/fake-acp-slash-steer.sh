#!/bin/sh
# Fake ACP agent for Hermes `/steer` tests — no `_session/steering` extension.
# Driven by crates/harness/tests/acp.rs (slash-steer scenarios).

emit() { printf '%s\n' "$1"; }
rid() { printf '%s' "$1" | sed 's/.*"id":\([0-9]*\).*/\1/'; }
has() { case "$1" in *"$2"*) return 0 ;; *) return 1 ;; esac; }

update() {
  emit "{\"method\":\"session/update\",\"params\":{\"sessionId\":\"$SID\",\"update\":$1}}"
}

read -r line || exit 1
has "$line" '"method":"initialize"' || exit 1
emit "{\"id\":$(rid "$line"),\"result\":{\"protocolVersion\":1,\"agentCapabilities\":{\"loadSession\":true}}}"

read -r line || exit 1
has "$line" '"method":"session/new"' || exit 1
SID="s-hermes"
emit "{\"id\":$(rid "$line"),\"result\":{\"sessionId\":\"$SID\"}}"

read -r promptline || exit 1
has "$promptline" '"method":"session/prompt"' || exit 1
pid=$(rid "$promptline")

case "$promptline" in

*scenario:slash-steer-immediate*)
  update '{"sessionUpdate":"tool_call","toolCallId":"sleep","title":"sleep","kind":"execute","status":"in_progress"}'
  read -r steerline || exit 1
  sid=$(rid "$steerline")
  has "$steerline" '"method":"session/prompt"' || exit 1
  has "$steerline" '/steer inject-now' || exit 1
  update '{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"⏩ Steer queued for the active turn: inject-now"}}'
  emit "{\"id\":$sid,\"result\":{\"stopReason\":\"end_turn\"}}"
  update '{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"after-steer"}}'
  update '{"sessionUpdate":"tool_call_update","toolCallId":"sleep","status":"completed"}'
  emit "{\"id\":$pid,\"result\":{\"stopReason\":\"end_turn\"}}"
  ;;

*scenario:slash-steer-fallback*)
  update '{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"working"}}'
  read -r steerline || exit 1
  sid=$(rid "$steerline")
  has "$steerline" '"method":"session/prompt"' || exit 1
  has "$steerline" '/steer boundary-text' || exit 1
  update '{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"No active turn - queued for the next turn."}}'
  emit "{\"id\":$sid,\"result\":{\"stopReason\":\"end_turn\"}}"
  emit "{\"id\":$pid,\"result\":{\"stopReason\":\"end_turn\"}}"
  read -r followline || exit 1
  fid=$(rid "$followline")
  if has "$followline" '"method":"session/prompt"' && has "$followline" 'boundary-text'; then
    update '{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"boundary-delivered"}}'
    emit "{\"id\":$fid,\"result\":{\"stopReason\":\"end_turn\"}}"
  else
    emit "{\"id\":$fid,\"result\":{\"stopReason\":\"refusal\"}}"
  fi
  ;;

*)
  emit "{\"id\":$pid,\"result\":{\"stopReason\":\"refusal\"}}"
  ;;
esac

cat >/dev/null
