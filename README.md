# EvoMK

A 2D physics sandbox built in Kotlin using libGDX and Box2D. It co-evolves body topologies alongside continuous neural controllers rewarded strictly on horizontal distance over 1,200 ticks.

This started out purely as a weekend toy project to see what would happen if I threw simulated physics and neural nets together without any rules. Going into it, I always had a hard time intuitively buying into biological evolution. For example, the leap from random mutations to something as specialized as a beaver building a dam felt kind of unrealistic to me. But watching uncoordinated muscle twitches accidentally stumble into a self-stabilizing flywheel completely changed how I think about emergent complexity.

Anyways, the plan was asupposed to be a modular framework with varying morphologies and environments. But flat-ground running alone produced such rich, unexpected mechanics that I dropped the extra scope to focus entirely on this single scenario.

---

### Case Study

The population runs at 2,000 creatures. Every time a new creature beats the previous milestone by 20m, it uses FFmpeg to capture a recording.

| Gen 38 (20m) | Gen 131 (81m) | Gen 212 (180m) | Gen 504 (396m) |
| :---: | :---: | :---: | :---: |
| <img width="180" height="180" alt="milestone_gen_38_fit_20_square" src="https://github.com/user-attachments/assets/02cd7b3c-a10e-49aa-93e5-f8f662fc7b89" /> | <img width="180" height="180" alt="milestone_gen_131_fit_81_square" src="https://github.com/user-attachments/assets/3d245454-d456-4378-b586-06eb286ee541" /> |<img width="180" height="180" alt="milestone_gen_212_fit_180_square" src="https://github.com/user-attachments/assets/cd9fbf1e-72ad-442c-a4e9-318129973e23" /> | <img width="180" height="180" alt="milestone_gen_504_fit_396_square" src="https://github.com/user-attachments/assets/04b74eb2-7f8a-439b-adc4-636773174be3" />|
| *Learning to jump a little bit* | *Consistent jumping* | *Discovers how to roll* | *High-speed momentum* |

Fitness graph:

<img width="600" height="400" alt="desmos-graph" src="https://github.com/user-attachments/assets/ee80104c-927d-4272-b4c9-3342d0e7bf4f" />

---

## Design Philosophy

- **Morphology Split Problem (Continuous vs. Discrete Body Mutations):**  
  The original goal was to evolve radically different body plans while preserving motor skills across lineage splits. To prevent sudden collapse in fitness, I tried to make the mutation logic as continuous as possible in terms of structure (dividing masses proportionally and cloning/splitting existing brain nodes to carry learned muscle memory into newly added limbs.) In practice, that did not work as well as I intended. The moment a new mass or spring enters the system, the body's mechanics changes completely, which almost always meant its fitness would decrease slightly. Because selection pressure is ruthless, any structural split caused a massive short-term fitness penalty, so populations naturally favored staying with the existing body plan and fine-tuning control weights instead.
  
- **Depth Over Artificial Modularity:**  
  Rather than forcing complex multi-body or multi-terrain mechanics that fight the optimizer, I leaned into the natural physics of flat-plane displacement ($X_{final} - X_{initial}$). Constraining the problem revealed that even a 4-node starter setup holds an enormous search space.

- **No Bias:**  
  The fitness function only measures forward displacement, meaning anything goes. If a creature figures out how to fling itself, roll, or exploit contact physics to travel farther than conventional walking, the engine rewards it.

- **Headless Simulation:**  
  Evolution happens headlessly across all CPU cores via Java thread pools, completely detached from graphics. Rendering only spins up to capture milestone replays.

### Tech & Features

- **Kotlin + libGDX + Box2D**: Simulation and physics rendering.
- **Multithreading**: Simulates population in parallel with Java thread pools.
- 
---

### Running It
This project currently exists as a bespoke research prototype and personal experiment. Reproducing the exact build requires manually configuring the libGDX environment and Box2D physics bindings. If there is enough interest, I can clean up the repository and provide a streamlined, one-click setup script.

### Things to try doing in the future:
* Speciation
* Sexual reproduction
* Dynamic mutation rates
* Create a better gui
* Coevolution (multiple creatures interacting in the same world)

### Gallery:
<details>
  <summary><b>Peak triangle (Gen 2074, Fitness 476)</b></summary>
  <br>

  https://github.com/user-attachments/assets/a37c6cdf-5229-4be8-b81c-50a89e7069e4

</details>

<details>
  <summary><b>Peak Pentagon (Gen 1572, Fitness 541)</b></summary>
  <br>

  https://github.com/user-attachments/assets/f3b06f68-9c67-4a91-a38e-da982e4a8672
</details>
<details>
  <summary><b>Creature loses balance and then gives up</b></summary>
  <br>
  
  https://github.com/user-attachments/assets/f0a19c19-2ecb-4238-83c8-7bd60a250b82
</details>


Before adding implementing recording to the simulation loop, dozens of weird, ephemeral body plans came and went unrecorded. Instead of burning CPU cycles trying to force the optimizer to stumble into the exact same evolutionary dead ends, I thought it would be cool to document them from memory, kind of like I'm Charles Darwin:


<details>
    <summary><b>Sketches</b></summary>
  
<img width="300" height="300" alt="2026_10_04_0og_Kleki" src="https://github.com/user-attachments/assets/3b97fe7e-ff9d-4cef-96dc-f74f9ccc2b48" />
<br>
<img width="300" height="300" alt="2026_10_04_0o9_Kleki" src="https://github.com/user-attachments/assets/6399fac8-b522-44dd-8da7-4d81b2734f9f" />
<br>
<img width="300" height="300" alt="2026_10_04_0ok_Kleki" src="https://github.com/user-attachments/assets/f60b115b-8692-49ea-9975-0aed7fa191e2" />
<br>
I'll add more if I remember any more. I think I sat at my computer for like 2 days trying to see what creatures could come up.

</details>





