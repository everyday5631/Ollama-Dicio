"""Tests for the parts that need neither a microphone nor a downloaded model."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from enclave_desktop.core import ollama_registry as reg  # noqa: E402
from enclave_desktop.core.protocol import (  # noqa: E402
    Message, ToolDef, ToolParam, build_prompt, index_of_complete_json_object, parse_tool_call,
)
from enclave_desktop.tools.builtin import CalculatorTool, evaluate_expression  # noqa: E402


class TestOllamaReference(unittest.TestCase):
    def test_bare_name_uses_library_and_latest(self):
        ref = reg.parse("tinydolphin")
        self.assertEqual(ref.host, "registry.ollama.ai")
        self.assertEqual(ref.namespace, "library")
        self.assertEqual(ref.name, "tinydolphin")
        self.assertEqual(ref.tag, "latest")
        self.assertEqual(
            ref.manifest_url,
            "https://registry.ollama.ai/v2/library/tinydolphin/manifests/latest",
        )

    def test_explicit_tag(self):
        ref = reg.parse("qwen2.5:0.5b")
        self.assertEqual(ref.name, "qwen2.5")
        self.assertEqual(ref.tag, "0.5b")

    def test_dot_in_name_is_not_a_host(self):
        self.assertEqual(reg.parse("qwen2.5").host, "registry.ollama.ai")
        self.assertEqual(reg.parse("qwen2.5").name, "qwen2.5")

    def test_two_parts_is_namespace_not_host(self):
        ref = reg.parse("myuser/mymodel:v2")
        self.assertEqual(ref.host, "registry.ollama.ai")
        self.assertEqual(ref.namespace, "myuser")
        self.assertEqual(ref.name, "mymodel")
        self.assertEqual(ref.tag, "v2")

    def test_leading_hostname_is_recognised(self):
        ref = reg.parse("hf.co/bartowski/model:Q4_K_M")
        self.assertEqual(ref.host, "hf.co")
        self.assertEqual(ref.namespace, "bartowski")
        self.assertEqual(ref.name, "model")
        self.assertEqual(ref.tag, "Q4_K_M")

    def test_port_is_not_a_tag(self):
        ref = reg.parse("localhost:11434/library/tinydolphin")
        self.assertEqual(ref.host, "localhost:11434")
        self.assertEqual(ref.name, "tinydolphin")
        self.assertEqual(ref.tag, "latest")

    def test_blob_url(self):
        self.assertEqual(
            reg.parse("tinydolphin").blob_url("sha256:abc"),
            "https://registry.ollama.ai/v2/library/tinydolphin/blobs/sha256:abc",
        )

    def test_urls_are_not_references(self):
        self.assertFalse(reg.is_reference("https://example.com/m.gguf"))
        self.assertFalse(reg.is_reference("http://example.com/m.gguf"))
        self.assertFalse(reg.is_reference("   "))
        self.assertTrue(reg.is_reference("tinydolphin"))


class TestToolCallParsing(unittest.TestCase):
    def test_plain_answer_is_not_a_tool_call(self):
        self.assertIsNone(parse_tool_call("It is currently raining."))

    def test_simple_call(self):
        call = parse_tool_call('{"tool": "current_time", "arguments": {}}')
        self.assertIsNotNone(call)
        self.assertEqual(call.name, "current_time")
        self.assertEqual(call.arguments, {})

    def test_call_wrapped_in_prose(self):
        call = parse_tool_call(
            'Sure! ```json\n{"tool": "calculate", "arguments": {"expression": "2+2"}}\n``` done'
        )
        self.assertEqual(call.name, "calculate")
        self.assertEqual(call.arguments["expression"], "2+2")

    def test_nested_braces(self):
        call = parse_tool_call('{"tool": "x", "arguments": {"a": {"b": 1}}}')
        self.assertEqual(call.arguments, {"a": {"b": 1}})

    def test_braces_inside_strings_do_not_confuse_the_scanner(self):
        call = parse_tool_call('{"tool": "say", "arguments": {"text": "a } brace"}}')
        self.assertEqual(call.name, "say")
        self.assertEqual(call.arguments["text"], "a } brace")

    def test_incomplete_object_is_not_yet_a_call(self):
        self.assertEqual(index_of_complete_json_object('{"tool": "x"'), -1)
        self.assertIsNone(parse_tool_call('{"tool": "x"'))

    def test_malformed_json_is_not_a_call(self):
        self.assertIsNone(parse_tool_call('{tool: current_time}'))

    def test_object_without_tool_key_is_not_a_call(self):
        self.assertIsNone(parse_tool_call('{"foo": "bar"}'))


class TestPrompt(unittest.TestCase):
    def test_tools_are_injected_into_the_system_message(self):
        tools = [ToolDef("calculate", "Do maths.", [ToolParam("expression", "the sum")])]
        prompt = build_prompt([Message("system", "You are Enclave."),
                               Message("user", "two plus two")], tools)
        self.assertIn("You are Enclave.", prompt)
        self.assertIn("calculate: Do maths.", prompt)
        self.assertTrue(prompt.endswith("<|im_start|>assistant\n"))

    def test_system_message_is_synthesised_when_absent(self):
        tools = [ToolDef("current_time", "Tell the time.")]
        prompt = build_prompt([Message("user", "what time is it")], tools)
        self.assertTrue(prompt.startswith("<|im_start|>system\n"))
        self.assertIn("current_time", prompt)

    def test_tool_results_are_labelled(self):
        prompt = build_prompt(
            [Message("user", "time?"), Message("tool", "It is 5pm.", tool_name="current_time")],
            [],
        )
        self.assertIn("Result of current_time: It is 5pm.", prompt)


class TestCalculator(unittest.TestCase):
    def test_arithmetic(self):
        self.assertEqual(evaluate_expression("2 + 3 * 4"), 14)
        self.assertEqual(evaluate_expression("(2 + 3) * 4"), 20)
        self.assertEqual(evaluate_expression("-5 + 2"), -3)

    def test_code_execution_is_rejected(self):
        for hostile in ["__import__('os').system('ls')", "open('/etc/passwd').read()",
                        "().__class__.__bases__[0]"]:
            with self.assertRaises(Exception):
                evaluate_expression(hostile)

    def test_huge_exponent_is_rejected(self):
        with self.assertRaises(ValueError):
            evaluate_expression("9**9**9")

    def test_tool_reports_division_by_zero(self):
        self.assertIn("division by zero", CalculatorTool().execute({"expression": "1/0"}))

    def test_tool_formats_integers_without_decimal_point(self):
        self.assertEqual(CalculatorTool().execute({"expression": "6/2"}), "6/2 is 3.")


if __name__ == "__main__":
    unittest.main()
