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
 return {int(a[1]):tuple(map(int,a[2:])) for a in (l.split() for l in Path('build/resource-smoke.txt').read_text().splitlines()) if a[0]=='item'}


def box(kind):
 for line in Path('build/resource-smoke.txt').read_text().splitlines():
  p=line.split()
  if p[0]=='box' and int(p[4])==kind:
   return dict(burn=int(p[5]),progress=int(p[6]),slots=[tuple(map(int,s.split(':'))) for s in p[7:]])
 raise AssertionError('missing container')
def choose(index):
 t.XTestFakeMotionEvent(d,0,135+index%9*36,169+index//9*40,0);x.XFlush(d);click()

def count(id):return sum(stack[1] for stack in items().values() if stack[0]==id)
def hold_mouse(seconds):
 t.XTestFakeButtonEvent(d,1,1,0);x.XFlush(d);time.sleep(seconds)
 t.XTestFakeButtonEvent(d,1,0,0);x.XFlush(d);time.sleep(.25)
hold_mouse(.95);key('F5');assert count(207)==1 and count(208)==0,items()
hold_mouse(.8);key('F5');assert items()[0]==(201,1,31) and count(208)==0,items()
shot('resource-gate');key('2');hold_mouse(1.55);key('F5')
assert count(208)==3 and items()[1]==(210,1,93),items()
key('d',1.0);key('f');choose(4)
for i in range(3):key('t')
choose(3);key('b');key('e');time.sleep(6.5);key('f');key('F5')
a=box(15);assert a['slots'][2]==(209,3,0),a
shot('resource-iron');key('g');key('e');key('e')
for i in range(3):key('Up')
key('Return');key('F5');assert count(213)==1 and count(209)==0 and items()[2]==(213,1,192),items()
shot('resource-iron-pickaxe');key('e');key('Escape');key('q')
print('PASS UI_COAL_GATE STONE_PICKAXE_THREE_IRON COAL_FUEL IRON_INGOTS IRON_PICKAXE_192')
