# Quantum Marble Maze

This is the source of Quantum Marble Maze. The game is available to play [here](http://tropic.org.uk/~crispin/quantum/) and the best written introduction is in [this blog post](https://omnisplore.wordpress.com/2016/04/25/learning-quantum-mechanics-the-easy-way/).

<img src="https://raw.githubusercontent.com/fiftysevendegreesofrad/quantum/master/screengrabs/Capture.PNG" width="400"/> <img src="https://raw.githubusercontent.com/fiftysevendegreesofrad/quantum/master/screengrabs/Capture2.PNG" width="400"/>

## What does the game need?

### Javascript port

Feedback from HN suggested strongly that the game should be rewritten in Javascript for better usability.  
This is probably a good first step.

### Need for speed

For the interested, here follows a short braindump on how the quantum part of this game could be improved.

The first and most worthy thing to do with this code would be speed up the quantum computation, which would allow
for more interesting level designs.  A defining feature of QMM version 1 is that uncertainty dominates – the particles spread 
out fast.  The faster the simulation, the bigger a simulation we can make, which means we can “zoom out” more from the
Planck length.  This would allow

1.	levels with more particle-like behaviour
2.	levels with multiple particles (see note below)

JS offers SIMD libraries now, which will speed up the computation.  But there are other ways of getting faster too.
If you can make use of OpenGL it would likely be faster again on GPU.  
A final option for increasing the speed is to change the algorithm.  The current computation is explicit, 
but I believe an implicit one can be faster.  I saw some benchmark data somewhere that showed this, bur can’t find it now, sorry.  These two SE posts make a good starting point:
http://physics.stackexchange.com/questions/138823/turning-a-finite-difference-equation-into-code-2d-schrodinger-equation
http://scicomp.stackexchange.com/questions/10876/are-there-simple-ways-to-numerically-solve-the-time-dependent-sch%c3%b6dinger-equatio/10880#10880

### Educational value

The HN thread has some good points from some users https://news.ycombinator.com/item?id=11813473

### Multiple particles

If you haven’t already, it’s worth reading my comments on this blog first
https://linkingideasblog.wordpress.com/2016/04/25/learning-quantum-mechanics-the-easy-way/

For gameplay purposes it would be possible to simulate multiple particles with O(n) complexity by making the particles non-interacting, *except* at a specific point in time or space.  This needs creation of some new gameplay device.  For example:

1.	Less interesting: particles are released if a trap or suchlike is triggered.  This isn’t really faithful to QM as it
implies wavefunction collapse - I appreciate the game already has collapses, but only because the gameplay
doesn't work without them, it would be better to keep to a minimum.

2.	How about a counter which counts down, and when it reaches zero, a new particle is created with wavefunction
determined by the wavefunction of the first particle.  The two wavefunctions are entangled, but continue
to evolve independently after the instant of interaction. Possibilities could be kept to a minimum by having only two
initial states for the 2nd particle depending on whether the first was in zone A or B.  
Thus there are only four wavefunctions to simulate: two particles times two states.  
To visualise perhaps a different colour to each entangled state, and within that state, having the two particles 
be the same colour.  The user can probably distinguish between them on the screen as they’ll be in
different places, for a while at least. 

3.	If you really get multiple order-of-magnitude speedups it might *just* be possible to simulate two
continuously interacting entangled particles on a very small scale.  To visualise perhaps one colour
per state, but with some sort of hill climbing simplification algorithm to only display the n most
likely sufficiently different states.

## License

The code is released under GPL v3, but an extra term is added to ensure any contributors to the game are credited in game:

*Derivative works must display the following list of credits at an appropriate point during the running of the executable.  In version 1 this happens on the game menu and game end screen but any reasonable variation is fine.  Other contributors are invited to add themselves to this list (both in-game and in this license) as appropriate.*

*“Original concept & development: Crispin Cooper”*

The level designs, graphics, music and embedded sounds are released under CC Attribution-NonCommercial-ShareAlike 4.0 Intl.
Note that levels with the NASA backdrop must also comply with CC Attribution as they currently do, and the Exo-Bold font is covered by the SIL Open Font License v1.10.
