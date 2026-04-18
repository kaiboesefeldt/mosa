For business logic and features, stay in a small TDD loop:

1. Pick one minimal behavior increment.
2. Add one failing automated test through public behavior.
3. Write the smallest production change that makes it pass.
4. Refactor production code without changing behavior if it improves clarity.
5. Refactor the test without changing behavior if it improves clarity.
6. Repeat until the subtask is done.
