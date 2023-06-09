# Some ground rules & Important Coding Conventions

### Variable Naming
1. Name all parameters verbosely. This makes grep'ing the code far easier than when names and types are ambiguous
   one example is, there exists a `SlidingSyncRoom` and a `Room`, naming parameters, variables and arguments as
   `room` only and not `slidingSyncRoom`; there's absolutely no way to easily grep for the difference.
2. See point one.
3. See point one.
4. See point one.

### 3rd party dependencies
Keep 3rd party dependencies to an absolute minimum. For a whole host of reasons.
Using 3rd party dependencies introduce mental overhead for anybody and anyone who isn't familiar
with those projects. For instance; Need to do some simple serialization? Write your own simple
serializing function. You do not need to pull in a 3rd party dep to write a few lines of text to a file.

3rd party deps introduce the following problems;
1. Add to build time
2. Increase mental overhead and creates local context reasoning problems
3. They usually solve way more problems than what you have, see points 1, 2