"""The tools the model can call.

Each tool declares a name, a description and its parameters -- which is what the model
sees -- plus an ``execute`` that returns a short string to speak or show. Adding a tool
means writing one class and listing it in :func:`default_tools`.

Everything here works offline. Nothing reaches the network.
"""

from __future__ import annotations

import ast
import logging
import operator
import os
import platform
import shutil
import subprocess
from abc import ABC, abstractmethod
from datetime import datetime

from ..core.protocol import ToolDef, ToolParam

log = logging.getLogger(__name__)


class Tool(ABC):
    """A capability exposed to the model."""

    definition: ToolDef

    @abstractmethod
    def execute(self, arguments: dict) -> str:
        """Run the tool and return a short human-readable result."""


class CurrentTimeTool(Tool):
    definition = ToolDef(
        name="current_time",
        description="Tell the user the current time and date.",
        params=[],
    )

    def execute(self, arguments: dict) -> str:
        now = datetime.now()
        return now.strftime("It is %H:%M on %A, %d %B %Y.")


# Only these operators are permitted in an expression; anything else is rejected before
# evaluation. This is what keeps `calculate` from being an arbitrary-code-execution hole.
_OPERATORS = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
    ast.FloorDiv: operator.floordiv,
    ast.Mod: operator.mod,
    ast.Pow: operator.pow,
    ast.USub: operator.neg,
    ast.UAdd: operator.pos,
}

_MAX_POW = 1_000_000
"""Guards against `9**9**9` turning a "calculation" into a denial of service."""


def _eval_node(node: ast.AST) -> float:
    if isinstance(node, ast.Constant):
        if isinstance(node.value, bool) or not isinstance(node.value, (int, float)):
            raise ValueError("only numbers are allowed")
        return node.value
    if isinstance(node, ast.BinOp):
        op = _OPERATORS.get(type(node.op))
        if op is None:
            raise ValueError("unsupported operator")
        left, right = _eval_node(node.left), _eval_node(node.right)
        if type(node.op) is ast.Pow and (abs(right) > 64 or abs(left) ** abs(right) > _MAX_POW):
            raise ValueError("exponent too large")
        return op(left, right)
    if isinstance(node, ast.UnaryOp):
        op = _OPERATORS.get(type(node.op))
        if op is None:
            raise ValueError("unsupported operator")
        return op(_eval_node(node.operand))
    raise ValueError("unsupported expression")


def evaluate_expression(expression: str) -> float:
    """Evaluate an arithmetic expression safely.

    Uses an AST walk over an explicit operator allow-list rather than :func:`eval`, so a
    model that emits ``__import__('os').system(...)`` gets a ValueError, not a shell.
    """
    if len(expression) > 200:
        raise ValueError("expression too long")
    tree = ast.parse(expression, mode="eval")
    return _eval_node(tree.body)


class CalculatorTool(Tool):
    definition = ToolDef(
        name="calculate",
        description="Evaluate an arithmetic expression, e.g. 12 * (3 + 4).",
        params=[ToolParam("expression", "the arithmetic expression to evaluate")],
    )

    def execute(self, arguments: dict) -> str:
        expression = str(arguments.get("expression", "")).strip()
        if not expression:
            return "I did not get an expression to calculate."
        try:
            result = evaluate_expression(expression)
        except ZeroDivisionError:
            return "That would be a division by zero."
        except Exception as exc:
            log.debug("Bad expression %r: %s", expression, exc)
            return f"I could not calculate {expression}."
        if isinstance(result, float) and result.is_integer():
            result = int(result)
        return f"{expression} is {result}."


class OpenApplicationTool(Tool):
    definition = ToolDef(
        name="open_application",
        description="Launch an application on this computer by name, e.g. firefox.",
        params=[ToolParam("name", "the name of the application to launch")],
    )

    def execute(self, arguments: dict) -> str:
        name = str(arguments.get("name", "")).strip()
        if not name:
            return "I did not get an application name."

        # only a bare executable name is accepted, and it must already be on PATH:
        # this keeps the model from launching an arbitrary path with arbitrary flags
        if not name.replace("-", "").replace("_", "").replace(".", "").isalnum():
            return f"{name} is not a valid application name."

        executable = shutil.which(name)
        if executable is None:
            return f"I could not find an application called {name}."

        try:
            if platform.system() == "Windows":
                subprocess.Popen([executable], close_fds=True)
            else:
                subprocess.Popen(
                    [executable],
                    start_new_session=True,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    close_fds=True,
                )
        except Exception as exc:
            log.warning("Could not launch %s: %s", executable, exc)
            return f"I could not start {name}."
        return f"Opening {name}."


class SystemInfoTool(Tool):
    definition = ToolDef(
        name="system_info",
        description="Report what operating system and machine this is running on.",
        params=[],
    )

    def execute(self, arguments: dict) -> str:
        return (
            f"Running {platform.system()} {platform.release()} "
            f"on {platform.machine()}, with {os.cpu_count()} CPU cores."
        )


def default_tools() -> list[Tool]:
    """The tools available to the model, in the order the prompt lists them."""
    return [CurrentTimeTool(), CalculatorTool(), OpenApplicationTool(), SystemInfoTool()]
