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
 return {int(a[1]):tuple(map(int,a[2:])) for a in (l.split() for l in Path('build/production-smoke.txt').read_text().splitlines()) if a[0]=='item'}


def box(kind):
 for line in Path('build/production-smoke.txt').read_text().splitlines():
  p=line.split()
  if p[0]=='box' and int(p[4])==kind:
   return dict(burn=int(p[5]),progress=int(p[6]),slots=[tuple(map(int,s.split(':'))) for s in p[7:]])
 raise AssertionError('missing container')
def choose(index):
 t.XTestFakeMotionEvent(d,0,135+index%9*36,169+index//9*40,0);x.XFlush(d);click()

time.sleep(3);key('f');key('F5')
a=box(15);assert a['slots'][2]==(17,2,0) and a['slots'][0][1]==0,a
shot('production-glass');key('g')
assert items()[0][:2]==(17,2),items()
key('e');key('d',1.0);key('f')
choose(2);key('t')
a=box(16);assert a['slots'][0]==(201,1,31),a
shot('production-chest-tool');key('e')
t.XTestFakeButtonEvent(d,1,1,0);x.XFlush(d);time.sleep(.8)
t.XTestFakeButtonEvent(d,1,0,0);x.XFlush(d);time.sleep(.25)
key('f');key('F5')
assert box(16)['slots'][0]==(201,1,31)
key('g');assert items()[2]==(201,1,31),items()
choose(0);key('t');assert box(16)['slots'][0]==(17,2,0)
shot('production-chest-glass');key('e');key('Escape');key('q')
print('PASS UI_FURNACE_RELOAD_GLASS CHEST_WORN_TOOL BREAK_GUARD WITHDRAW_PRESERVES_WEAR GLASS_STORAGE')
