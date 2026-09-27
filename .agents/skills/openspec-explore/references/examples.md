# Optional explore examples

These examples illustrate discussion and diagrams. The skill body retains every mandatory scope, capture, context and artifact rule.

## Focused discovery example

For example, after inspecting the relevant code:

```text
The CLI already uses SQLite and has no remote service. Is sharing state
across devices in scope? That determines whether local storage is enough.
If this stays a single-device tool, I recommend keeping SQLite to avoid
adding a service to operate; shared state would need a separate sync design.
```


## Diagram gallery

**Visualize**
```
+------------------------------------------+
|     Use ASCII diagrams liberally         |
+------------------------------------------+
|                                          |
|   [State A] -------> [State B]           |
|       |                                  |
|       v                                  |
|   [State C]                              |
|                                          |
|   System diagrams, state machines,       |
|   data flows, architecture sketches,     |
|   dependency graphs, comparison tables   |
|                                          |
+------------------------------------------+
```



## Handling Different Entry Points

**User brings a vague idea:**
```
User: I'm thinking about adding real-time collaboration

You: Real-time collab is a big space. Let me think about this...

      COLLABORATION SPECTRUM
      ============================================

      Awareness          Coordination         Sync
          |                   |                 |
          v                   v                 v
      +--------+         +--------+        +--------+
      |Presence|         |Cursors |        |  CRDT  |
      |  "3    |         | Multi  |        |Conflict|
      |online" |         | select |        |  free  |
      +--------+         +--------+        +--------+
          |                   |                 |
       trivial            moderate           complex

      Where's your head at?
```

**User brings a specific problem:**
```
User: The auth system is a mess

You: [reads codebase]

     +---------------------------------------------+
     |              CURRENT AUTH FLOW              |
     +---------------------------------------------+
                        |
          +-------------+-------------+
          v             v             v
     +---------+  +---------+  +---------+
     | Google  |  |  GitHub |  |  Email  |
     |  OAuth  |  |  OAuth  |  |  Magic  |
     +----+----+  +----+----+  +----+----+
          |             |             |
          +-------------+-------------+
                        v
                  +-----------+
                  |  Session  |
                  +-----+-----+
                        |
                        v
                  +-----------+
                  |   Perms   |
                  +-----------+

     I see three tangles. Which one's burning?
```

**User is stuck mid-implementation:**
```
User: $openspec-explore (Codex) or /openspec-explore (other agents) add-auth-system
      The OAuth integration is more complex than expected

You: [reads change artifacts]

     You're on task 4: "Implement OAuth flow"

     Let me trace what's involved...

     [draws diagram, explores options, suggests paths]

     Want to update the design to reflect this?
     Or add a spike task to investigate?
```

**User wants to compare options:**
```
User: Should we use Postgres or SQLite?

You: Generic answer is boring. What's the context?

User: A CLI tool that tracks local dev environments

You: That changes everything.

     +-------------------------------------------------+
     |          CLI TOOL DATA STORAGE                  |
     +-------------------------------------------------+

     Key constraints:
     - No daemon running
     - Must work offline
     - Single user

                  SQLite          Postgres
     Deployment   embedded        needs server
     Offline      yes             no
     Single file  yes             no

     SQLite. Not even close.

     Unless... is there a sync component?
```

---

## Ending Discovery

There's no required ending. Discovery might:

- **Flow into a proposal**: "Ready to start? I can create a change proposal."
- **Result in artifact updates**: "Updated design.md with these decisions"
- **Just provide clarity**: User has what they need, moves on
- **Continue later**: "We can pick this up anytime"

When it feels like things are crystallizing, you might summarize:

```
## What We Figured Out

**The problem**: [crystallized understanding]

**The approach**: [if one emerged]

**Open questions**: [if any remain]

**Next steps** (if ready):
- Create a change proposal
- Keep exploring: just keep talking
```

But this summary is optional. Sometimes the thinking IS the value.

---
