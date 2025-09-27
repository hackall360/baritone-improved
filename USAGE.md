(assuming you already have Baritone [set up](SETUP.md))

# Prefix

Baritone's chat control prefix is `#` by default. In Impact, you can also use `.b` as a prefix. (for example, `.b click` instead of `#click`)

Baritone commands can also by default be typed in the chatbox. However if you make a typo, like typing "gola 10000 10000" instead of "goal" it goes into public chat, which is bad, so using `#` is suggested.

To disable direct chat control (with no prefix), turn off the `chatControl` setting. To disable chat control with the `#` prefix, turn off the `prefixControl` setting. In Impact, `.b` cannot be disabled. Be careful that you don't leave yourself with all control methods disabled (if you do, reset your settings by deleting the file `minecraft/baritone/settings.txt` and relaunching).

# For Baritone 1.2.10+, 1.3.5+, 1.4.2+

Lots of the commands have changed, BUT `#help` is improved vastly (its clickable! commands have tab completion! oh my!).

Try `#help` I promise it won't just send you back here =)

"wtf where is cleararea" -> look at `#help sel`

"wtf where is goto death, goto waypoint" -> look at `#help wp` 

just look at `#help` lmao

Watch this [showcase video](https://youtu.be/CZkLXWo4Fg4)!

# Commands

## Seed Cracking (experimental)

- `#crackseed add <village|feature|monument|endcity|slime> <chunkX> <chunkZ>` Adds an observation.
- `#crackseed start` Begins an async lower-48 seed search (GPU if available, CPU fallback).
- `#crackseed status` Shows progress and current candidates found.
- `#crackseed reset` Clears observations; `#crackseed use <seedLong>` sets `overrideWorldSeed`.

Notes: Works best with multiple observations (e.g., 2+ structures). If you know the full seed, set `#set overrideWorldSeed <long>`.
