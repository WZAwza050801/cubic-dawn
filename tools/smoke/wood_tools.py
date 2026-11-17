import ctypes as c, time, subprocess
from pathlib import Path
x=c.CDLL('libX11.so.6'); t=c.CDLL('libXtst.so.6')
x.XOpenDisplay.argtypes=[c.c_char_p]; x.XOpenDisplay.restype=c.c_void_p
x.XStringToKeysym.argtypes=[c.c_char_p]; x.XStringToKeysym.restype=c.c_ulong
x.XKeysymToKeycode.argtypes=[c.c_void_p,c.c_ulong];x.XKeysymToKeycode.restype=c.c_uint
x.XSetInputFocus.argtypes=[c.c_void_p,c.c_ulong,c.c_int,c.c_ulong]
x.XFlush.argtypes=[c.c_void_p]
t.XTestFakeKeyEvent.argtypes=[c.c_void_p,c.c_uint,c.c_int,c.c_ulong]
t.XTestFakeButtonEvent.argtypes=[c.c_void_p,c.c_uint,c.c_int,c.c_ulong]
t.XTestFakeMotionEvent.argtypes=[c.c_void_p,c.c_int,c.c_int,c.c_int,c.c_ulong]
d=x.XOpenDisplay(b':98'); assert d
window=int(subprocess.check_output(['xwininfo','-display',':98','-root','-tree'],text=True).split('"Cubic Dawn"')[0].split()[-1],16)
x.XSetInputFocus(d,window,1,0);x.XFlush(d)
def key(name,hold=.20):
 code=x.XKeysymToKeycode(d,x.XStringToKeysym(name.encode()))
 t.XTestFakeKeyEvent(d,code,1,0); x.XFlush(d);time.sleep(hold)
 t.XTestFakeKeyEvent(d,code,0,0); x.XFlush(d);time.sleep(.25)
def click(button=1):
 t.XTestFakeButtonEvent(d,button,1,0);x.XFlush(d);time.sleep(.2)
 t.XTestFakeButtonEvent(d,button,0,0);x.XFlush(d);time.sleep(.3)
def shot(name):
 output=Path('build/ui-smoke');output.mkdir(parents=True,exist_ok=True)
 subprocess.run(['import','-display',':98','-window','root',str(output/(name+'.png'))],check=True)
def items():
 return {int(a[1]):(int(a[2]),int(a[3])) for a in (l.split() for l in Path('build/tools-smoke.txt').read_text().splitlines()) if a[0]=='item'}

def count(id): return sum(n for item,n in items().values() if item==id)
def hold_mouse(seconds):
 t.XTestFakeButtonEvent(d,1,1,0);x.XFlush(d);time.sleep(seconds)
 t.XTestFakeButtonEvent(d,1,0,0);x.XFlush(d);time.sleep(.3)
hold_mouse(.4)
assert count(11)==8,items()
# Opening inventory cancels mining; holding through resume must not restart it.
t.XTestFakeButtonEvent(d,1,1,0);x.XFlush(d);time.sleep(.2)
key('e');time.sleep(.8);assert count(11)==8,items()
key('e');time.sleep(1.1);assert count(11)==8,items()
t.XTestFakeButtonEvent(d,1,0,0);x.XFlush(d);time.sleep(.3)
hold_mouse(1.1);assert count(11)==9,items()
key('e')
key('Down');key('Down')
for i in range(3): key('Return')
assert count(13)==12,items()
key('Down');key('Return')
key('Down');key('Return')
assert count(200)==4 and count(14)==1,items()
key('Down');key('Down');key('Return')
assert count(201)==0 and count(13)==6,items()
shot('tools-bench-required')
key('e');key('5');click(3)
assert count(14)==0,items()
key('e');key('Return')
assert count(201)==1 and count(13)==3 and count(200)==2,items()
t.XTestFakeMotionEvent(d,0,252,200,0);x.XFlush(d);click();key('9')
assert items()[8]==(11,6),items()
t.XTestFakeMotionEvent(d,0,692,200,0);x.XFlush(d);click();key('1')
assert items()[0]==(11,6) and items()[8]==(0,0),items()
t.XTestFakeMotionEvent(d,0,472,200,0);x.XFlush(d);click()
shot('tools-crafted-pickaxe')
key('e');key('5');hold_mouse(2.2)
assert count(14)==1 and count(3)==1 and count(201)==1,items()
key('F5');shot('tools-mined-stone');key('Escape');key('q')
print('PASS UI_MINING_CANCEL_ON_INVENTORY UI_LOG_MINING PLANKS STICKS WORKBENCH BENCH_GATE WOOD_PICKAXE TOOL_MINING SAVE_QUIT')
